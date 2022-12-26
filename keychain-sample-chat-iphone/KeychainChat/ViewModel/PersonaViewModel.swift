//
//  PersonaViewModel.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 8/16/21.
//

import SwiftUI
import Foundation

import Logging
import OrderedCollections

class PersonaViewModel: ObservableObject, Refreshable, ViewAction {
    let log = Logger(label: PersonaViewModel.typeName)
    
    var personas = [Persona]()
    var usersDict = [String: User]()
    
    @Published var users = [User]()
    
    @State var progressShowing = false
    
    var pendingPersonas: Dictionary<String, PendingPersona> = Dictionary()
    
    var activePersona: Persona?
    
    private var keychainService = KeychainService.instance
    
    var enableRefresh = true
    var updateUsers = false
    
    let personasLock = NSLock()
    let updatingUsersLock = NSLock()
    
    fileprivate func updateUsersDict(_ tempUsers: inout [User], _ incoming: Dictionary<String, User>.Element) {
        tempUsers.append(incoming.value)
        usersDict.removeValue(forKey: incoming.value.getName())
        usersDict[incoming.value.getKey()] = incoming.value
    }
    
    fileprivate func updateStatusOfExistingUsers(tempUsers: inout [User], platformUsers: [String: User]) {
        for incoming in platformUsers {
            guard let user = usersDict[incoming.key] else {
                updateUsersDict(&tempUsers, incoming)
                continue
            }
            
            // If the status of the existing user is different from the incomming
            if getPersonaStatus(status: PersonaStatus(rawValue: user.status ?? 0) ?? .unknown) !=
                getPersonaStatus(status: PersonaStatus(rawValue: incoming.value.status ?? 0) ?? .unknown) {
                
                updateUsersDict(&tempUsers, incoming)
            } else if !tempUsers.isEmpty {
                // If at least one status updated, we need to copy the rest of the users so all
                // users will apprear in the UI. Otherwise, only the updated ones will appear
                tempUsers.append(user)
            }
        }
    }
    
    fileprivate func preservePendingPersonas(tempUsers: inout [User], platformUsers: [String: User]) {
        for user in platformUsers.values {
            if user.status == PersonaStatus.confirmed.rawValue {
                pendingPersonas.removeValue(forKey: user.getKey())
                continue
            }
            
            guard let firstName = user.firstName,
                  let lastName = user.lastName else {
                continue
            }
            
            guard let persona = keychainService?.findPersona(firstName: firstName, lastName: lastName, uri: nil) else {
                continue
            }
            
            guard let status = user.status else {
                continue
            }
            
            do {
                guard try status != persona.getStatus().intValue else {
                    continue
                }
            } catch {
                log.warning("Error getting status of chat user: \(user.getName())")
            }
            
            let pending = PendingPersona()
            pending.recordId = user.id
            pending.persona = persona
            
            if let photoUrl = user.photo {
                pending.image = loadProfileImage(fileURL: URL(fileURLWithPath: photoUrl.replacingOccurrences(of: "file:///", with: "/")))
            }
            
            let pUser = createPendingUser(persona, recordId: user.id)
            pUser.photo = user.photo
            
            tempUsers.insert(pUser, at: 0)
            
            // At first the pending user has no uri, so the name is used as the key
            // After the uri exists we use the uri as the key
            usersDict.removeValue(forKey: pUser.getName())
            usersDict[pUser.getKey()] = pUser
            
            saveToDatabase(persona: persona)
        }
    }
    
    fileprivate func addChatUsers(_ platformUsers: [String : User]) {
        do {
            // Make sure all personas are added as chat users
            for p in personas {
                guard usersDict[getUriString(p)] == nil else {
                    continue
                }
                
                let firstName = getLeftName(p)
                let lastName = getRightName(p)
                let personaStatus = try p.getStatus().intValue
                let uriString = getUriString(p)
                
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
                            log.warning("Unable to retrieve chat user: \(getName(p))")
                            return
                        }
                    })
                })
            }
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
        }
    }
    
    fileprivate func getUsers() {
        if !personas.isEmpty {
            guard let personasDict = keychainService?.getPersonasDictionary() else {
                return
            }
            
            keychainService?
                .chatRepository
                .getPlatformUsers(filterBy: personasDict) { [self] platformUsers in
                    
                    log.info("personas.count = \(personas.count)")
                    log.info("pendingPersonas.values.count = \(pendingPersonas.values.count)")
                    log.info("platformUsers.count = \(platformUsers.count)")
                    log.info("usersDict.values.count \(usersDict.values.count)")
                    log.info("users.count = \(users.count)")
                    log.info("")
                    
                    guard updatingUsersLock.try() else {
                        return
                    }
                    
                    defer {
                        updatingUsersLock.unlock()
                    }
                    
                    // Update UI
                    DispatchQueue.main.async { [self] in
                        if platformUsers.values.count > 0 {
                            var tempUsers = [User]()
                            
                            preservePendingPersonas(tempUsers: &tempUsers, platformUsers: platformUsers)
                            updateStatusOfExistingUsers(tempUsers: &tempUsers, platformUsers: platformUsers)
                            
                            log.info("tempUsers.count = \(tempUsers.count)")
                            log.info("")
                            
                            Utils.sortUsers(users: &users, usersDict: usersDict)
                        }
                    }
                }
        }
    }
    
    func loadProfileImage(fileURL: URL) -> UIImage? {
        
        do {
            log.info("Loading profile image: \(fileURL.absoluteString)")
            let imageData = try Data(contentsOf: fileURL)
            return UIImage(data: imageData)
        } catch {
            log.error("Error loading image: \(error)")
        }
        
        return nil
    }
    
    fileprivate func saveConfirmedPersonaToDB(_ unconfirmed: PendingPersona, _ persona: Persona, _ name: String) {
        do {
            guard try persona.getStatus().intValue == PersonaStatus.confirmed.rawValue else {
                return
            }
            
            guard let unconfirmedPersona = unconfirmed.persona else {
                return
            }
            
            let unconfirmedUri = getUriString(unconfirmedPersona)
            let uri = getUriString(persona)
            
            log.info("Updating persona URI: \(name)")
            log.info("Unconfirmed URI: \(unconfirmedUri)")
            log.info("Confirmed URI: \(uri)")
            
            // Pesona just get confirmed, now save the db with correct URI
            saveToDatabase(persona: persona)
            pendingPersonas.removeValue(forKey: name)
            usersDict.removeValue(forKey: name)
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
        }
    }
    
    func getUser(persona: Persona) -> User {
        do {
            if let user = usersDict[try persona.getUri().toString()] {
                return user
            }
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
        }
        
        return User()
    }
    
    func onRefresh() {
        do {
            if !updateUsers && pendingPersonas.isEmpty && !personas.isEmpty {
                // Only update persons if there is one in process
                // of maturing
                return
            }

            if !updateUsers && !enableRefresh {
                return
            }
            
            updateUsers = false
            
            if personasLock.try() {
                defer {
                    personasLock.unlock()
                }
                
                personas = keychainService?.getPersonas() ?? []
                
                getUsers()
                
                // Add any contats if they are not already in the chat db
                addChatUsers(usersDict)

                for p in personas {
                    let name = getName(p)
                    
                    // Check if persona is mature
                    if try p.getStatus().intValue == PersonaStatus.confirmed.rawValue {
                        guard let unconfirmed = pendingPersonas[name] else {
                            continue
                        }
                        
                        saveConfirmedPersonaToDB(unconfirmed, p, name)
                        progressShowing = false
                        updateUsers = true
                    } else {
                        guard let unconfirmed = pendingPersonas[name] else {
                            continue
                        }
                        
                        // Reflect updated status
                        unconfirmed.persona = p
                        
                        var usersArr = [User]()
                        
                        for user in users {
                            if user.firstName == getLeftName(p) && user.lastName == getRightName(p) {
                                let updatedUser = createPendingUser(p, recordId: user.id)
                                usersArr.append(updatedUser)
                            } else {
                                usersArr.append(user)
                            }
                        }
                        
                        // Force screen to update
                        users = usersArr
                    }
                }
            }
        } catch {
            log.error("\(Constants.somethingWentWrong)): \(error)")
        }
    }
    
    func onViewAppeared() {
        keychainService?.pauseMonitor()
        keychainService?.registerRefreshListener(name: self.typeName, refreshable: self)
        keychainService?.resumeMonitor()
        
        getUsers()
        
        enableRefresh = true
    }
    
    func onViewDisappeared() {
        enableRefresh = false
        keychainService?.unregisterRefreshListener(self.typeName)
    }
    
    func isActivePersona(previous: Persona, current: Persona) throws -> Bool {
        return try previous.getName() == current.getName() && previous.getSubName() == current.getSubName()
    }
    
    func createPersona(firstName: String,
                       lastName: String,
                       image: UIImage?,
                       completion: @escaping (_ result: Persona?, _ image: UIImage?) -> ()) {
        do {
            if let found = try keychainService?.getPersonasDictionary().contains(where: { (key: String, value: Facade) in
                var isMatch = try value.getName() == firstName
                isMatch = try isMatch && value.getSubName() == lastName
                
                return isMatch
            }) {
                if found {
                    log.warning("Persona already exists for \(firstName) \(lastName)")
                    completion(keychainService?.findPersona(firstName: firstName, lastName: lastName, uri: nil),
                               image)
                }
            }
            
            let persona = keychainService?.createPersona(firstName: firstName,
                                                         lastName: lastName,
                                                         level: .medium)
            
            if let persona = persona {
                if personasLock.try() {
                    defer {
                        personasLock.unlock()
                    }
                    
                    personas.insert(persona, at: 0)
                }
            }
            
            log.debug("Persona created for \(firstName) \(lastName)")
            
            onRefresh()
            
            completion(persona, image)
        } catch {
            log.error("Error saving persona. \(error)")
            return completion(nil, nil)
        }
    }
    
    func createPendingUser(_ persona: Persona, recordId: String) -> User {
        let user = User()
        
        do {
            user.id = recordId
            user.firstName = getLeftName(persona)
            user.lastName = getRightName(persona)
            user.uri = user.id
            user.status = try persona.getStatus().intValue
        } catch {
            log.error("\(Constants.somethingWentWrong) \(error)")
        }
        
        return user
    }

    func deletePersona(_ user: User) {
        personasLock.lock()
        
        defer {
            personasLock.unlock()
        }
        
        log.info("Delting persona: \(user.getName())")
        
        usersDict.removeValue(forKey: user.getKey())
        
        guard  let uri = user.uri, let persona = getPersona(uri: uri) else {
            log.warning("Attempting to delete persona. But persona not found for: \(user.getName())")
            return
        }
        
        guard let keychainService = keychainService else {
            log.warning("\(Constants.somethingWentWrong)")
            return
        }
        
        if keychainService.deletePersona(persona) {
            log.info("Successfully deleted persona: \(user.getName())")
        } else {
            log.info("Failed to deleted persona: \(user.getName())")
        }
        
        updateUsers = true
        onRefresh()
    }
    
    func shouldDisablePersona(_ persona: Persona) -> Bool {
        do {
            return  try !persona.isMature().boolValue
        } catch {
            log.error("Error in shouldDisablePersona: \(error)")
            return false
        }
    }
    
    func getPersona(id: Int) -> Persona? {
        do {
            for persona in personas {
                if try persona.getId().intValue == id {
                    return persona
                }
            }
        } catch {
            log.error("Error getting persona: \(error)")
        }
        
        return nil
    }
    
    func getPersona(uri: String?) -> Persona? {
        do {
            guard let uri = uri else {
                return nil
            }
            
            for persona in personas {
                if try persona.getUri().toString() == uri {
                    return persona
                }
            }
        } catch {
            log.error("Error getting persona: \(error)")
        }
        
        return nil
    }
    
    func getLeftName(_ persona: Persona) -> String {
        do {
            return try persona.getName()
        } catch {
            log.error("Error getting persona name \(error)")
            return ""
        }
    }
    
    func getRightName(_ persona: Persona) -> String {
        do {
            return try persona.getSubName()
        } catch {
            log.error("Error getting persona subName \(error)")
        }
        
        return ""
    }
    
    func getName(_ persona: Persona) -> String {
        return "\(getLeftName(persona)) \(getRightName(persona))"
    }
    
    func getUriString(_ persona: Persona) -> String {
        do {
            return try persona.getUri().toString()
        } catch {
            log.error("Error getting persona subName \(error)")
        }
        
        return ""
    }
    
    func getPersonaStatus(status: PersonaStatus) -> Text {
        var personaStatus: Text
        
        switch status {
        case PersonaStatus.unknown:
            personaStatus = Text(NSLocalizedString("Unknown", comment: ""))
                .foregroundColor(.red)
            
        case .created:
            personaStatus = Text(NSLocalizedString("Created", comment: ""))
                .foregroundColor(Color("created-status"))
            
        case .funding:
            personaStatus = Text(NSLocalizedString("Funding", comment: ""))
                .foregroundColor(.orange)
            
        case .broadcasted:
            personaStatus = Text(NSLocalizedString("Broadcasted", comment: ""))
                .foregroundColor(.orange)
            
        case .confirming:
            personaStatus = Text(NSLocalizedString("Confirming", comment: ""))
                .foregroundColor(.orange)
            
        case .confirmed:
            personaStatus = Text(NSLocalizedString("Confirmed", comment: ""))
                .foregroundColor(Color("confirmed-status"))
            
        case .expiring:
            personaStatus = Text(NSLocalizedString("Expiring", comment: ""))
                .foregroundColor(.red)
            
        case .expired:
            personaStatus = Text(NSLocalizedString("Expired", comment: ""))
                .foregroundColor(.red)
            
        @unknown default:
            personaStatus = Text(NSLocalizedString("Unknown", comment: ""))
                .foregroundColor(.red)
        }
        
        return personaStatus
            .fontWeight(.semibold)
    }
    
    func getPersonaStatus(_ persona: Persona) -> Text {
        do {
            if let status = try PersonaStatus(rawValue: persona.getStatus().intValue) {
                return getPersonaStatus(status: status)
            }
        } catch {
            log.error("Error getting persona status: \(error)")
        }
        
        return Text(NSLocalizedString("Unknown", comment: ""))
            .foregroundColor(.red)
    }
    
    func getQRCodeData(persona: Persona) -> String {
        do {
            let data = try AccountQRCodeData(id: persona.getUri().toString(),
                                             firstName: persona.getName(),
                                             lastName: persona.getSubName())
            
            guard let json = JSONUtils.toJson(target: data) else {
                log.error("Unable to generate JSON data from AccountQRCodeData")
                return ""
            }
            
            log.info("Generated JSON for QRCode:")
            log.info("\(json)")
            
            return json
        } catch {
            log.error("\(error)")
        }
        
        return ""
    }
    
    fileprivate func savePersonaToUsersTable(_ firstName: String, _ lastName: String, _ image: UIImage?) {
        keychainService?.chatRepository.saveUserProfile(firstName: firstName,
                                                        lastName: lastName,
                                                        status: PersonaStatus.created.rawValue,
                                                        uri: UUID().uuidString,
                                                        image: image,
                                                        completion: { [self] recordId in
            if let recordId = recordId {
                log.info("Successfully saved pending persona to database for: \(firstName) \(lastName), recordId: \(recordId)")
                
                keychainService?.chatRepository.getPlatformUser(recordId: recordId) { [self] (user) in
                    guard let user = user else {
                        log.error("Failed to find user in database for: \(firstName) \(lastName), recordId: \(recordId)")
                        return
                    }
                    
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [self] in
                        usersDict[user.getKey()] = user
                        users.insert(user, at: 0)
                    }
                }
            } else {
                log.error("Failed to save pending persona to database for: \(firstName) \(lastName)")
            }
        })
    }
    
    func renamePersona(persona: Persona, newName: String, newSubName: String) {
        do {
            let name = try persona.getName()
            let subName = try persona.getSubName()
            
            log.debug("\(Constants.renamingPersona) from (\(name) \(subName)) to (\(newName) \(newSubName))")
            
            keychainService?.modifyPersona(persona: persona,
                                           newFirstName: newName,
                                           newLastName: newSubName)
            
            let uri = getUriString(persona)
            
            keychainService?.chatRepository.updateUserProfile(uri: uri,
                                                              firstName: newName,
                                                              lastName: newSubName,
                                                              completion: { [self] recordId in
                if recordId == nil {
                    log.warning("Error renaming chat user in db")
                    return
                }
                
                updateUsers = true
                usersDict.removeValue(forKey: uri)
                onRefresh()
            })
        } catch {
            log.error("Error renaming persona: \(error)")
        }

    }
    
    func savePersona(firstName: String, lastName: String, image: UIImage?) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [self] in
            progressShowing = true
        }
        
        DispatchQueue.global(qos: .userInteractive).async { [self] in
            log.debug("\(Constants.savingPersona) \(firstName) \(lastName)")
            
            createPersona(firstName: firstName, lastName: lastName, image: image) { [self](persona, image) in
                
                if let persona = persona {
                    log.debug("\(Constants.personaSavedSuccessfully) \(firstName) \(lastName)")
                    
                    keychainService?.chatRepository.updateUserProfile(firstName: firstName,
                                                                      lastName: lastName,
                                                                      status: PersonaStatus.created.rawValue,
                                                                      uri: getUriString(persona),
                                                                      completion: { [self] recordId in
                        
                        if let recordId = recordId {
                            log.info("Successfully updated pending persona for: \(firstName) \(lastName), recordId: \(recordId.utf8CString)")
                            
                            let pending = PendingPersona()
                            pending.persona = persona
                            pending.image = image
                            pending.recordId = recordId
                            
                            pendingPersonas[getName(persona)] = pending
                        } else {
                            log.error("Failed to save pending persona to database for: \(firstName) \(lastName)")
                        }
                    })
                } else {
                    log.error("\(Constants.personaSaveFailed)")
                }
            }
        }

        savePersonaToUsersTable(firstName, lastName, image)
        
        DispatchQueue.global(qos: .userInteractive).async { [self] in
            log.debug("\(Constants.savingPersona) \(firstName) \(lastName)")
            
            createPersona(firstName: firstName, lastName: lastName, image: image) { [self](persona, image) in
                
                if let persona = persona {
                    log.debug("\(Constants.personaSavedSuccessfully) \(firstName) \(lastName)")
                    
                    keychainService?.chatRepository.updateUserProfile(firstName: firstName,
                                                                      lastName: lastName,
                                                                      status: PersonaStatus.created.rawValue,
                                                                      uri: getUriString(persona),
                                                                      completion: { [self] recordId in
                        
                        if let recordId = recordId {
                            log.info("Successfully updated pending persona for: \(firstName) \(lastName), recordId: \(recordId.utf8CString)")
                            
                            let pending = PendingPersona()
                            pending.persona = persona
                            pending.image = image
                            pending.recordId = recordId
                            
                            pendingPersonas[getName(persona)] = pending
                        } else {
                            log.error("Failed to save pending persona to database for: \(firstName) \(lastName)")
                        }
                    })
                } else {
                    log.error("\(Constants.personaSaveFailed)")
                }
            }
        }
    }
    
    func saveToDatabase(persona: Persona) {
        do {
            let uri = try persona.getUri().toString()
            let firstName = try persona.getName()
            let lastName = try persona.getSubName()
            let status = try persona.getStatus().intValue
            
            keychainService?.chatRepository.updateUserProfile(firstName: firstName,
                                                              lastName: lastName,
                                                              status: status,
                                                              uri: uri) { [self] recordId in
                if let recordId = recordId {
                    log.info("Persona successfully updated in chats database for \(firstName) \(lastName)")
                    log.info("RecordId \(recordId.utf8CString) - \(firstName) \(lastName) - URI: \(uri)")
                    
                    let pendingPersona = pendingPersonas[getName(persona)]
                    pendingPersona?.recordId = recordId
                } else {
                    log.error("Error updating persona profile to chats database for \(firstName) \(lastName)")
                }
            }
        } catch {
            log.error("\(Constants.personaSaveFailed) \(error)")
        }
    }
}

extension PersonaViewModel : TypeNameDescribable {}
