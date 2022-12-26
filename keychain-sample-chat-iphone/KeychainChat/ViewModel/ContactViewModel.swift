//
//  ContactViewModel.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 8/29/21.
//

import Foundation
import SwiftUI

import Logging
import OrderedCollections

class ContactViewModel: ObservableObject, Refreshable {
    
    let log = Logger(label: ContactViewModel.typeName)
    
    var users = [User]()
    var usersDict = [String: User]()
    
    private var filterText = ""
    @Published var filteredUsers = [User]()
    
    public var contacts = [Contact]()

    private var keychainService = KeychainService.instance
    
    public var enableRefresh = true
    
    private var updateContacts = true
    
    let contactsLock = NSLock()
    
    func reset() {
        enableRefresh = false
        contacts.removeAll()
        users.removeAll()
        usersDict.removeAll()
        filteredUsers.removeAll()
        filterText = ""
    }
    
    func getLeftName(_ contact: Contact) -> String {
        do {
            return try contact.getName()
        } catch {
            log.error("Error getting contact name \(error)")
            return ""
        }
    }
    
    func getRightName(_ contact: Contact) -> String {
        do {
            return try contact.getSubName()
        } catch {
            log.error("Error getting contact subName \(error)")
        }
        
        return ""
    }
    
    func getName(_ contact: Contact) -> String {
        return "\(getLeftName(contact)) \(getRightName(contact))"
    }
    
    func getUriString(_ contact: Contact) -> String {
        do {
            return try contact.getUri().toString()
        } catch {
            log.error("Error getting contact subName \(error)")
        }
        
        return ""
    }
    
    func getQRCodeView(_ qrCodeData: String) -> QRCodeView {
        let base64Encoded = (qrCodeData.convertToJsonString ?? "").toBase64()
        var qrCodeView = QRCodeView(stringData: base64Encoded)
        
        qrCodeView.width = 100
        qrCodeView.height = 100
        qrCodeView.alignment = .leading
        
        return qrCodeView
    }
    
    func processQRCodeScanResult(_ rawResult: String) -> ContactScanResult {
        log.info("Got data from QR code scan: \(rawResult)")
        
        guard let qrCodeData = JSONUtils.fromJson(AccountQRCodeData.self,
                                                  json: rawResult),
              let json = qrCodeData.convertToJsonString else {
                  
                  log.error("Unable to decode QR code scan")
                  return ContactScanResult(status: .error, qrCodeData: nil)
              }
        
        log.info("Successfully decoded QRCode data: ")
        log.info("\(json)")
        
        return ContactScanResult(status: .new, qrCodeData: qrCodeData)
    }
    
    func contactExists(_ uri: String) -> Bool {
        return keychainService?.findContact(uri) != nil
    }
    

    fileprivate func addChatUsers(_ platformUsers: [String : User]) {
        do {
            // Make sure all contacts are added as chat users
            for c in contacts {
                guard usersDict[getUriString(c)] == nil else {
                    continue
                }
                
                let firstName = getLeftName(c)
                let lastName = getRightName(c)
                let personaStatus = try c.getStatus().intValue
                let uriString = getUriString(c)
                
                if uriString.count < 100 {
                    log.warning("Contact has invalid uri. Deleting it: \(firstName) \(lastName): \(uriString)")
                    keychainService?.deleteContact(contact: c)
                    continue
                }
                
                keychainService?.chatRepository.saveUserProfile(firstName: firstName,
                                                                lastName: lastName,
                                                                status: personaStatus,
                                                                uri: uriString,
                                                                image: nil, completion: { [self] recordId in
                    guard recordId != nil else {
                        return
                    }
                    
                    keychainService?.chatRepository.getPlatformUser(uri: uriString, completion: { [self] user in
                        guard let _ = user else {
                            log.warning("Unable to retrieve chat user: \(getName(c))")
                            return
                        }
                    })
                })
            }
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
        }
    }

    func getChatContacts() {
        
        // Perform the contact store method asynchronously so it doesn't block the UI
        DispatchQueue.init(label: "getContacts").async { [self] in
            // See which local contacts are actually users of this app
            
            guard let contactsDict = keychainService?.getContactsDictionary() else {
                return
            }
            
            keychainService?
                .chatRepository
                .getPlatformUsers(filterBy: contactsDict) { platformUsers in
                
                // Update the UI in the main thread
                DispatchQueue.main.async { [self] in
                    // Set the fetched users to the published users property
                    for user in platformUsers.values {
                        guard let uri = user.uri else {
                            continue
                        }
                        
                        if contactsDict[uri] == nil {
                            continue
                        }
                        
                        users.append(user)
                        usersDict[user.getKey()] = user
                    }
                    
                    log.info("contacts.count = \(contacts.count)")
                    log.info("platformUsers.count = \(platformUsers.count)")
                    log.info("usersDict.values.count \(usersDict.values.count)")
                    log.info("users.count = \(users.count)")
                    log.info("")
                    
                    Utils.sortUsers(users: &users, usersDict: usersDict)

                    // Set the filtered list
                    filterContacts(filterBy: self.filterText)
                }
            }
        }
    }
    
    func filterContacts(filterBy: String) {
        
        // Store parameter into property
        self.filterText = filterBy
        
        // If filter text is empty, then reveal all users
        if filterText == "" {
            self.filteredUsers = users
            return
        }
        
        // Run the users list through the filter term to get a list of filtered users
        self.filteredUsers = users.filter({ user in
            
            // Criteria for including this user into filtered users list
            user.firstName?.lowercased().contains(filterText) ?? false ||
            user.lastName?.lowercased().contains(filterText) ?? false
           
        })
    }
    
    /// Given a list of user ids, return a list of user object that have the same user ids
    func getParticipants(ids: [String]) -> [User] {
        
        // Filter out the users list for only the participants based on ids passed in
        let foundUsers = users.filter { user in
            
            if user.uri == nil {
                return false
            } else {
                guard let uri = user.uri else {
                    return false
                }
                
                return ids.contains(uri)
            }
        }
        
        return foundUsers
    }
    
    func onRefresh() {
        if !updateContacts && (!enableRefresh || contacts.count > 0) {
            return
        }
        
        updateContacts = false
        
        if contactsLock.try() {
            defer {
                contactsLock.unlock()
            }
            
            contacts = keychainService?.getContacts() ?? []

            getChatContacts()
            
            // Add any contats if they are not already in the chat db
            addChatUsers(usersDict)
        }
    }
    
    func deleteContact(_ user: User) {
        defer {
            contactsLock.unlock()
        }

        do {
            contactsLock.lock()

            guard let uri = user.uri else {
                log.error("Contact has no uri")
                return
            }
            
            guard let contact = keychainService?.findContact(uri) else {
                log.error("Cannot find contact for uri: \(uri)")
                return
            }
            
            let name = try contact.getName()
            let subName = try contact.getSubName()
            
            log.info("Deleting contact: \(name) \(subName): \(uri)")
                    
            keychainService?.deleteContact(contact: contact)
            keychainService?.chatRepository.deleteUser(uri: uri, completion: { [self] recordId in
                if (recordId == nil) {
                    log.warning("Contact not deleted from db: \(name) \(subName): \(uri)")
                }
            })
                        
            var userNdx = -1
            var i = -1
            
            for user in users {
                i += 1
                
                if user.uri == uri {
                    userNdx = i
                    break
                }
            }
            
            guard userNdx >= 0 else {
                return
            }
            
            guard users[userNdx].uri == uri else {
                return
            }
            
            users.remove(at: userNdx)
            usersDict.removeValue(forKey: uri)
            
            updateContacts = true
            onRefresh()
        } catch {
            log.error("Error deleting contact: \(error)")
        }
    }
    
    func renameContact(contact: Contact, newName: String, newSubName: String) {
        do {
            let name = try contact.getName()
            let subName = try contact.getSubName()
            
            log.debug("\(Constants.renamingContact) from (\(name) \(subName)) to (\(newName) \(newSubName))")
            
            keychainService?.modifyContact(contact: contact, firstName: newName, lastName: newSubName)
            
            let uri = getUriString(contact)
            
            keychainService?.chatRepository.updateUserProfile(uri: uri,
                                                              firstName: newName,
                                                              lastName: newSubName,
                                                              completion: { [self] recordId in
                if recordId == nil {
                    log.warning("Error renaming chat user in db")
                    return
                }
                
                updateContacts = true
                usersDict.removeValue(forKey: uri)
                onRefresh()
            })
        } catch {
            log.error("Error renaming persona: \(error)")
        }

    }

    func modifyContact(contact: Contact, name: String, subName: String) {
        DispatchQueue.main.async { [self] in
            keychainService?.modifyContact(contact: contact, firstName: name, lastName: subName)
            contacts = keychainService?.getContacts() ?? []
        }
    }
    
    func getContact(uri: String?) -> Contact? {
        do {
            guard let uri = uri else {
                return nil
            }
            
            for contact in contacts {
                if try contact.getUri().toString() == uri {
                    return contact
                }
            }
        } catch {
            log.error("Error getting contact: \(error)")
        }
        
        return nil
    }

    func onViewAppeared() {
        enableRefresh = false
        keychainService?.registerRefreshListener(name: self.typeName, refreshable: self)
        
        getChatContacts()

        enableRefresh = true
    }
}

extension ContactViewModel : TypeNameDescribable {}
