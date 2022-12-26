//
//  keychainService.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 10/28/21.
//

import Foundation
import os
import SwiftUI

import Logging


class KeychainService {
    let log = Logger(label: KeychainService.typeName)
    
    @Published var activePersona: Persona?
    
    var chatRepository: ChatRepository = SQLiteDBService()

    var wasMonitorStarted = false
    
    let appConfig = AppConfig.get()
    
    var configPath: String?
    var dropDbPath: String?
    var createDbPath: String?
    var dbPath: String?
    
    static let instance: KeychainService? = {
        do {
            let instance = try KeychainService()

            return instance
        } catch {
            Logger(label: KeychainService.typeName).error("Error initializing gateway: \(error)")
        }
        
        return nil
    }()
    
    var gateway: Gateway?
    
    private var monitorService: MonitorService?
    
    private init() throws {
        try initializePaths()
        
        guard let configFile = configPath,
              let dropDbFile = dropDbPath,
              let createDbFile = createDbPath,
              let dbFilePath = dbPath else {
                  throw KeychainError.unexpected
              }
        
        let databasePath = "\(dbFilePath)/keychain.db"
        
        gateway = try Gateway(configFile, databasePath, false, dropDbFile, createDbFile)
        monitorService = try MonitorService(dbFilePath: databasePath, gateway: gateway, refreshInterval: appConfig.refreshInterval)
        
        let address = ""
        let mnemonicList = NSMutableArray()
        try gateway?.seed(address, mnemonicList)
        
        try chatRepository.createOrOpenDB()
    }
    
    func initializePaths() throws {
        configPath = try UtilsFiles.getPath(fileName: "keychain", extention: "cfg")
        dropDbPath = try UtilsFiles.getPath(fileName: "drop_keychain", extention: "sql")
        createDbPath = try UtilsFiles.getPath(fileName: "keychain", extention: "sql")
        dbPath = UtilsFiles.getDocumentsDirectory().path
    }
    
    func startMonitor() throws {
        monitorService?.setState(.started)
        wasMonitorStarted = true
    }
    
    func stopMonitor() {
        monitorService?.setState(.stopped)
        wasMonitorStarted = false
    }
    
    func pauseMonitor() {
        monitorService?.setState(.paused)
    }
    
    func resumeMonitor() {
        if !wasMonitorStarted {
            do {
                try startMonitor()
            } catch {
                log.critical("Unable to start monitor")
            }
            
            return
        }
        
        monitorService?.setState(.resumed)
    }
    
    func registerRefreshListener(name: String, refreshable: Refreshable) {
        monitorService?.addListener(name: name, listener: refreshable);
    }
    
    func unregisterRefreshListener(_ name: String) {
        monitorService?.removeListener(name);
    }
    
    func getPersonas() -> [Persona] {
        do {
            guard let personas = try gateway?.getPersonas() else {
                return []
            }
            
            return personas
        } catch {
            log.error("Error getting personas: \(error)")
        }
        
        return []
    }
    
    public func setActivePersona(_ persona: Persona?) -> Bool {
        if let p = persona {
            do {
                try gateway?.setActivePersona(p)
                activePersona = p
                
                let name = try p.getName()
                let subName = try p.getSubName()
                
                log.info("Active persona set to: \(name) \(subName)")
                return true
            } catch {
                log.error("Error settings active persona: \(error)")
            }
        }
        
        return false
    }
    
    public func  findContact(_ uri: String) -> Contact? {
        do {
            guard let contacts = try gateway?.getContacts() else {
                return nil
            }
            
            for contact in contacts {
                if try contact.getUri().toString() == uri {
                    return contact
                }
            }
        } catch {
            log.error("Error while finding contact for uri \(uri)")
            log.error("\(error)")
        }
        
        return nil
    }
    
    public func  findContact(firstName: String, lastName: String, uri: String?) -> Contact? {
        do {
            guard let contacts = try gateway?.getContacts() else {
                return nil
            }
            
            for contact in contacts {
                if try firstName == contact.getName() && lastName == contact.getSubName() {
                    return contact
                }
                
                if let theUri = uri {
                    if try theUri == contact.getUri().toString() {
                        return contact
                    }
                }
            }
        } catch {
            log.error("Error checking contacts for existence: \(error)");
        }
        
        return nil;
    }
    
    public func  findPersona(_ uri: String) -> Persona? {
        do {
            guard let persona = try gateway?.getPersonas() else {
                return nil
            }
            
            for contact in persona {
                if try contact.getUri().toString() == uri {
                    return contact
                }
            }
        } catch {
            log.error("Error while finding contact for uri \(uri)")
            log.error("\(error)")
        }
        
        return nil
    }
    
    public func  findPersona(firstName: String, lastName: String, uri: String?) -> Persona? {
        do {
            guard let persona = try gateway?.getPersonas() else {
                return nil
            }
            
            for contact in persona {
                if try firstName == contact.getName() && lastName == contact.getSubName() {
                    return contact
                }
                
                if let theUri = uri {
                    if try theUri == contact.getUri().toString() {
                        return contact
                    }
                }
            }
        } catch {
            log.error("Error checking contacts for existence: \(error)");
        }
        
        return nil;
    }
    
    public func getActivePersona() -> Persona? {
        self.activePersona = gateway?.getActivePersona()
        return self.activePersona
    }
    
    public func createPersona(firstName: String, lastName: String, level: SecurityLevel) -> Persona? {
        do {
            return try gateway?.createPersona(firstName, lastName, level)
        } catch {
            log.error("Error creating persona: \(error)")
        }
        
        return nil
    }
    
    public func deletePersona(_ persona: Persona) -> Bool {
        do {
            try gateway?.delete(persona)
            return true
        } catch {
            log.error("Error deleting persona: \(error)")
        }
        
        return false
    }

    public func createContact(name: String, subName: String, uri: Uri) -> Contact? {
        do {
            return try gateway?.createContact(name, subName, uri)
        } catch {
            log.error("Error creating contact: \(error)")
        }
        
        return nil
    }
    
    public func deleteContact(_ contact: Contact) -> Bool {
        do {
            try gateway?.delete(contact)
            return true
        } catch {
            log.error("Error deleting contact: \(error)")
        }
        
        return false
    }
   
    public func getPersonasDictionary()  -> [String : Facade] {
        var dictionary = [String : Persona]()
        
        do {
            for persona in getPersonas() {
                dictionary[try persona.getUri().toString()] = persona
            }
        } catch {
            log.error("Error occurred in getPersonasDictionary: \(error)")
        }
        
        return dictionary
    }
    
    public func getContactsDictionary() -> [String : Facade] {
        var dictionary = [String : Contact]()
        
        do {
            for contact in getContacts() {
                dictionary[try contact.getUri().toString()] = contact
            }
        } catch {
            log.error("Error occurred in getContactsDictionary: \(error)")
        }
        
        return dictionary
    }
    
    public func getContacts() -> [Contact] {
        do {
            guard let contacts = try gateway?.getContacts() else {
                return []
            }
            
            return contacts
        } catch {
            log.error("Error occurred in getContacts: \(error)")
        }
        
        return []
    }
     
    public func deleteContact(contact: Contact) {
        do {
            try gateway?.delete(contact)
        } catch {
            log.error("Error deleting contact: \(error)")
        }
    }
    
    public func modifyPersona(persona: Persona, newFirstName: String, newLastName: String) {
        do {
            try gateway?.renamePersona(persona, newFirstName, newLastName)
        } catch {
            log.error("Error renaming persona: \(error)")
        }
    }

    public func modifyContact(contact: Contact, firstName: String, lastName: String) {
        do {
            try gateway?.renameContact(contact, firstName, lastName)
        } catch {
            log.error("Error renaming contact: \(error)")
        }
    }
    
    public func signThenEncrypt(contacts: [Contact], message: String) -> String? {
        do {
            let utf8Bytes = message.utf8Bytes
            let data = Data(bytes: utf8Bytes, count: utf8Bytes.count);
            let message = String( data: data, encoding: .utf8)
            
            let encryptedMsg = try gateway?.signThenEncrypt(contacts, message)

            log.info("Signed and encrypted message:")
            log.info("\(encryptedMsg ?? "")")
            
            return encryptedMsg
        } catch {
            log.error("Error signing and encrypting message: \(error)")
        }
        
        return nil
    }
    
    public func decryptThenVerify(message: String) -> String? {
        do {
            guard StringUtils.isXML(text: message) else {
                log.warning("Attempting to decrypt a message that is not in XML format: \(message)")
                return nil
            }
            
            let decrypted = try gateway?.decryptThenVerify(message)
            return decrypted
        } catch {
            log.error("Error decrypting and verifying message: \(error)")
        }
        
        return nil
    }
}

extension KeychainService : TypeNameDescribable {}
