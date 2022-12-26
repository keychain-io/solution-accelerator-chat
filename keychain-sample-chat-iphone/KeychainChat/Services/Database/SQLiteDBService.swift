//
//  SQLiteDBService.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/06/10.
//

import Foundation
import SwiftUI

import Logging
import SQLite

class SQLiteDBService: ChatRepository {
    let log = Logger(label: SQLiteDBService.typeName)
    let config = AppConfig.get()
    
    var db: Connection?
    
    let dbName = "\(Constants.chats).\(Constants.sqliteExtention)"
    
    let usersTable = Table("users")
    let id = Expression<String>("id")
    let firstName = Expression<String?>("firstName")
    let lastName = Expression<String?>("lastName")
    let status = Expression<Int?>("status")
    let photo = Expression<String?>("photo")
    let uri = Expression<String?>("uri")
    
    let chatsTable = Table("chats")
    let chatId = Expression<String>("id")
    let participantIds = Expression<String>("participantIds")
    let lastMsg = Expression<String?>("lastMsg")
    let chatTimestamp = Expression<String?>("timestamp")
    
    let messagesTable = Table("messages")
    let msgId = Expression<String>("id")
    let msgChatId = Expression<String>("chatId")
    let msgSendOrRcvd = Expression<String>("sendOrRcvd")
    let msgSenderId = Expression<String>("senderId")
    let msgRecieverId = Expression<String>("receiverId")
    let msgImageUrl = Expression<String?>("imageUrl")
    let msgMessage = Expression<String?>("msg")
    let msgTimestamp = Expression<String>("timestamp")
    
    var imagesPath: URL?
    
    func createOrOpenDB() throws {
        var dbPath = ""
        var isCreateNew = false
        
        let documentDirectory = UtilsFiles.getDocumentsDirectory()
        
        if UtilsFiles.exists(name: Constants.chats, ofType: Constants.sqliteExtention) {
            dbPath = documentDirectory
                .appendingPathComponent("\(Constants.chats).\(Constants.sqliteExtention)")
                .absoluteString
        } else {
            do {
                log.warning("Cannot find chats.sqlite3. Maybe first time run. Will attempt to create it.")
                
                imagesPath = documentDirectory
                    .appendingPathComponent("chats", isDirectory: true)
                    .appendingPathComponent("images", isDirectory: true)
                    .absoluteURL
                
                log.info("Images will be saved to: \(String(describing: imagesPath?.absoluteString))")
                
                try UtilsFiles.createDirectory(path: imagesPath!)
                
                dbPath = "\(documentDirectory)/\(Constants.chats).\(Constants.sqliteExtention)"
                
                isCreateNew = true
            } catch {
                log.error("\(Constants.somethingWentWrong): \(error)")
            }
        }
        
        db = try Connection(dbPath)
        log.info("Successfully connected to \(dbPath)")
        
        if isCreateNew || config.resetChatDB {
            try initializeDB(database: db)
        }
        
        getPlatformUser(firstName: Constants.all,
                        lastName: "") { [self] user in
            guard user == nil else {
                return
            }
            
            saveUserProfile(firstName: Constants.all,
                            lastName: "",
                            status: PersonaStatus.confirmed.rawValue,
                            uri: Constants.all, image: nil) { [self] recordId in
                
                log.info("Created user for \(Constants.all) chat")
            }
        }
    }
    
    func initializeDB(database: Connection?) throws {
        let fileName = "\(Constants.chats).\(Constants.sql)"
        
        guard let file = Bundle.main.url(forResource: Constants.chats, withExtension: Constants.sql) else {
            throw KeychainError.runtimeError("Cannot load file: \(fileName)")
        }
        
        let script = try String(contentsOf: file, encoding: String.Encoding.utf8 )
        
        try database?.execute(script)
        
        log.info("Successfully created database using script: \(fileName)")
    }
    
    func getPlatformUsers(filterBy: [String: Facade], completion: @escaping ([String : User]) -> Void) {
        var usersDict = [String : User]()
        
        guard let db = db else {
            log.error("\(Constants.noDatabaseConnection)")
            return
        }
        
        do {
            for row in try db.prepare(usersTable) {
                let status = row[status]
                
                if status == PersonaStatus.confirmed.rawValue {
                    guard let uri = row[uri] else {
                        continue
                    }
                    
                    if filterBy[uri] == nil {
                        continue
                    }
                }
                
                let user = User()
                
                user.id = row[id]
                user.firstName = row[firstName]
                user.lastName = row[lastName]
                user.status = status
                user.photo = row[photo]
                user.uri = row[uri]
                
                usersDict[user.getKey()] = user
            }
        } catch {
            log.error("Error retrieving users from DB: \(error)")
        }
        
        completion(usersDict)
    }
    
    func getPlatformUser(recordId: String, completion: @escaping (User?) -> Void) {
        do {
            guard let db = db else {
                log.error("\(Constants.noDatabaseConnection)")
                return completion(nil)
            }
            
            let filteredUser = usersTable.filter(id == recordId)
            
            for row in try db.prepare(filteredUser) {
                let user = User()
                user.id = row[id]
                user.firstName = row[firstName]
                user.lastName = row[lastName]
                user.status = row[status]
                user.photo = row[photo]
                user.uri = row[uri]
                
                return completion(user)
            }
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
        }
        
        completion(nil)
    }
    
    func getPlatformUser(uri: String, completion: @escaping (User?) -> Void) {
        do {
            guard let db = db else {
                log.error("\(Constants.noDatabaseConnection)")
                return completion(nil)
            }
            
            let filteredUser = usersTable.filter(self.uri == uri)
            
            for row in try db.prepare(filteredUser) {
                let user = User()
                user.id = row[id]
                user.firstName = row[firstName]
                user.lastName = row[lastName]
                user.status = row[status]
                user.photo = row[photo]
                user.uri = row[self.uri]
                
                return completion(user)
            }
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
        }
        
        completion(nil)
    }
    
    func getPlatformUser(firstName: String, lastName: String, completion: @escaping (User?) -> Void) {
        do {
            guard let db = db else {
                log.error("\(Constants.noDatabaseConnection)")
                return completion(nil)
            }
            
            let filteredUser = usersTable.filter(self.firstName == firstName && self.lastName == lastName)
            
            for row in try db.prepare(filteredUser) {
                let user = User()
                user.id = row[id]
                user.firstName = row[self.firstName]
                user.lastName = row[self.lastName]
                user.status = row[status]
                user.photo = row[photo]
                user.uri = row[uri]
                
                return completion(user)
            }
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
        }
        
        completion(nil)
    }

    func saveUserProfile(firstName: String, lastName: String, status: Int, uri: String?, image: UIImage?, completion: @escaping (String?) -> Void) {
        do {
            guard let db = db else {
                log.warning("\(Constants.noDatabaseConnection)")
                return completion(nil)
            }
            
            guard let uri = uri else {
                throw KeychainError.runtimeError("Uri string is nil")
            }
            
            var existing: User?
            
            getPlatformUser(firstName: firstName, lastName: lastName) { user in
                existing = user
            }
            
            if existing != nil {
                return completion(existing?.id)
            }
            
            var imageURL: URL?
            
            // Check if an image is passed through
            if let image = image, let imagesPath = imagesPath {
                
                // Create full path to where image will be stored
                imageURL = imagesPath.appendingPathComponent("\(UUID().uuidString).jpg")
                
                if let imageURL = imageURL {
                    // Turn our image into JPEG data
                    let imageData = image.jpegData(compressionQuality: 0.8)
                    
                    // Check that we were able to convert it to data
                    guard imageData != nil else {
                        return completion(nil)
                    }
                    
                    log.info("Saving image to \(imageURL.absoluteString)")
                    
                    // Save image to disk
                    try imageData?.write(to: imageURL, options: .atomic)
                }
            }
            
            let id = UUID().uuidString
            
            let insert = usersTable.insert(self.id <- id,
                                           self.firstName <- firstName,
                                           self.lastName <- lastName,
                                           self.status <- status,
                                           self.uri <- uri,
                                           self.photo <- imageURL?.absoluteString)
            
            _ = try db.run(insert)
            
            log.info("Successfully inserted chat user: \(firstName) \(lastName)")
            
            return completion(id)
        } catch {
            log.error("Error saving user profile: \(error)")
        }

        completion(nil)
    }
    
    func updateUserProfile(uri: String, firstName: String, lastName: String?, completion: @escaping (String?) -> Void) {
        do {
            guard let db = db else {
                log.warning("\(Constants.noDatabaseConnection)")
                return
            }
            
            let user = usersTable.filter(self.uri == uri)
            
            if try db.run(user.update(self.firstName <- firstName,
                                      self.lastName <- lastName)) > 0 {
                log.info("Successfully updated chat user name: \(firstName) \(lastName ?? "")")
            } else {
                return completion(nil)
            }

            let id = "\(user[self.id])"
            
            return completion(id)
        } catch {
            log.error("Error updating chat user name: \(error)")
        }

        completion(nil)
    }

    func updateUserProfile(firstName: String, lastName: String, status: Int, uri: String?, completion: @escaping (String?) -> Void) {
        do {
            guard let db = db else {
                log.warning("\(Constants.noDatabaseConnection)")
                return
            }
            
            let user = usersTable.filter(self.firstName == firstName && self.lastName == lastName)
            
            guard let uri = uri else {
                throw KeychainError.runtimeError("Uri is nil")
            }
            
            if try db.run(user.update(self.status <- status)) > 0 {
                log.info("Successfully updated chat user status: \(firstName) \(lastName): \(status)")
            } else {
                return completion(nil)
            }
            
            do {
                if try db.run(user.update(self.uri <- uri)) > 0 {
                    
                    log.info("Successfully updated chat user uri: \(firstName) \(lastName)")
                } else {
                    return completion(nil)
                }
            } catch {
                log.warning("Warning updating chat user uri (\(uri)): \(error)")
            }

            let id = "\(user[self.id])"
            
            return completion(id)
        } catch {
            log.error("Error updating user profile: \(error)")
        }

        completion(nil)
    }
    
    func userExists(firstName: String, lastName: String) -> Statement.Element? {
        do {
            guard let db = db else {
                log.error("\(Constants.noDatabaseConnection)")
                return nil
            }
            
            let sql = "SELECT EXISTS(SELECT 1 FROM users WHERE firstName=\"\(firstName)\" AND lastName=\"\(lastName)\";"
            
            log.info("Executing SQL: \(sql)")
            
            for row in try db.prepare(sql) {
                // User exists
                return row
            }
        } catch {
            log.error("Error saving user profile: \(error)")
        }
        
        return nil
    }

    func deleteUser(uri: String, completion: @escaping (String?) -> Void) {
        do {
            guard let db = db else {
                log.warning("\(Constants.noDatabaseConnection)")
                return
            }
            
            let user = usersTable.filter(self.uri == uri)
            
            if try db.run(user.delete()) > 0 {
                log.info("Successfully deleted chat user for uri: \(uri)")
            } else {
                return completion(nil)
            }

            let id = "\(user[self.id])"
            
            return completion(id)
        } catch {
            log.error("Error updating user profile: \(error)")
        }

        completion(nil)
    }
    
    // Get all chats where current persona (senderId) is a participant
    func getAllChats(senderId: String, completion: @escaping ([Chat]) -> Void) {
        var chats = [Chat]()
        
        guard let db = db else {
            log.error("\(Constants.noDatabaseConnection)")
            return
        }
        
        do {
            var allChat: Chat?
            
            for row in try db.prepare(chatsTable) {
                let participants = row[self.participantIds]
                let updated = Utils.dateFromString(row[self.chatTimestamp] ?? "")
                
                let participantArray = participants.components(separatedBy: "|")
                
                if StringUtils.arrayContains(array: participantArray, element: senderId) ||
                    StringUtils.arrayContains(array: participantArray, element: Constants.all) {
                    
                    if StringUtils.arrayContains(array: participantArray, element: Constants.all) {
                        // Only take the first one
                        if allChat == nil {
                            allChat = Chat()
                            
                            allChat?.id = row[id]
                            allChat?.participantIds = [Constants.all, Constants.all]
                            allChat?.lastMsg = row[self.lastMsg]
                            allChat?.timestamp = updated
                            
                            guard let allChat = allChat else {
                                // This will never happen. But is neccessary to make swift happy
                                continue
                            }

                            chats.insert(allChat, at: 0)
                        }
                        
                        continue
                    }

                    var chat = Chat()
                    
                    chat.id = row[id]
                    chat.participantIds = participantArray
                    chat.lastMsg = row[self.lastMsg]
                    chat.timestamp = updated
                    
                    chats.append(chat)
                }
            }
        } catch {
            log.error("Error retrieving chats from DB: \(error)")
        }

        completion(chats)
    }

    func getChat(senderId: String, receiverId: String, completion: @escaping (Chat?) -> Void) {
        log.info("Finding chat for participants \(senderId) AND \(receiverId)")
        
        guard let db = db else {
            log.error("\(Constants.noDatabaseConnection)")
            return
        }
        
        do {
            
            let filteredChats = (receiverId == Constants.all)
            ? chatsTable.filter(self.participantIds.like("%\(Constants.all)%"))
            : chatsTable.filter(self.participantIds.like("%\(senderId)%") && self.participantIds.like("%\(receiverId)%"))
            
            for row in try db.prepare(filteredChats) {
                
                let participants = row[self.participantIds]
                let updated = Utils.dateFromString(row[self.chatTimestamp] ?? "")
                
                let participantArray = participants.components(separatedBy: "|")
                
                var chat = Chat()
                
                chat.id = row[id]
                chat.participantIds = participantArray
                chat.lastMsg = row[self.lastMsg]
                chat.timestamp = updated
                
                return completion(chat)
            }
        } catch {
            log.error("Error retrieving chat from DB: \(error)")
        }

        completion(nil)
    }

    func saveChat(chat: Chat, completion: @escaping (String?) -> Void) {
        var recordId: String? = nil
        
        guard let db = db else {
            log.warning("\(Constants.noDatabaseConnection)")
            return
        }
        
        guard chat.participantIds?.count == 2 else {
            log.error("Invalid number of participants")
            return completion(nil)
        }
        
        guard let senderId = chat.participantIds?[0], let receiverId = chat.participantIds?[1] else {
            log.error("Unable to get senderId or receiverId for chat")
            return completion(nil)
        }
        
        getChat(senderId: senderId, receiverId: receiverId) { [self] existingChat in
            do
            {
                if existingChat != nil {
                    let chatRecord = chatsTable.filter(self.participantIds.like("%\(senderId)%") && self.participantIds.like("%\(receiverId)%"))
                    
                    if try db.run(chatRecord.update(self.lastMsg <- chat.lastMsg,
                                                    self.chatTimestamp <- Utils.dateToString(chat.timestamp ?? Date.now))) > 0 {
                        
                        log.info("Successfully updated chat for sender: \(senderId)")
                        recordId = existingChat?.id
                        return
                    } else {
                        recordId = nil
                        return
                    }
                }
                
                let id = chat.id ?? UUID().uuidString
                
                let insert = chatsTable.insert(self.chatId <- id,
                                               self.participantIds <- "\(senderId)|\(receiverId)",
                                               self.lastMsg <- chat.lastMsg,
                                               self.chatTimestamp <- Utils.dateToString(chat.timestamp ?? Date.now))
                
                _ = try db.run(insert)
                
                log.info("Successfully inserted chat sender: \(senderId)")
                
                recordId = id
            } catch {
                log.error("Error getring chat for sender: \(senderId)")
            }
            
            return
        }
        
        completion(recordId)
    }
    
    func updateChat(id: String, lastMessage: String) {
        do {
            guard let db = db else {
                log.warning("\(Constants.noDatabaseConnection)")
                return
            }
            
            let chat = chatsTable.filter(self.chatId == id)
            
            if try db.run(chat.update(self.lastMsg <- lastMessage,
                                      self.chatTimestamp <- Utils.dateToString(Date.now))) > 0 {
                
                log.info("Successfully updated chat, recordId: \(id)")
            } else {
                log.error("Failed to update chat.")
            }
        } catch {
            log.error("Error saving user profile: \(error)")
        }
    }

    func getAllMessages(chat: Chat, completion: @escaping ([ChatMessage]) -> Void) {
        var messages = [ChatMessage]()
        
        guard let db = db else {
            log.error("\(Constants.noDatabaseConnection)")
            return
        }
        
        guard let chatId = chat.id else {
            return
        }
        
        let filteredMessages = messagesTable.filter(self.msgChatId == chatId)
        
        do {
            for row in try db.prepare(filteredMessages) {
                let timestamp = Utils.dateFromString(row[self.chatTimestamp] ?? "")
                
                var msg = ChatMessage()
                
                msg.id = row[self.msgId]
                msg.chatId = row[self.msgChatId]
                msg.sendOrRcvd = ChatDirection(rawValue: row[self.msgSendOrRcvd])
                msg.senderId = row[self.msgSenderId]
                msg.receiverId = row[self.msgRecieverId]
                msg.imageUrl = row[self.msgImageUrl]
                msg.msg = row[self.msgMessage]
                msg.timestamp = timestamp?.millisecondsSince1970

                messages.append(msg)
            }
        } catch {
            log.error("Error retrieving messages from DB: \(error)")
        }
        
        completion(messages)
    }
    
    func isMessageExisting(recordId: String) -> Bool? {
        guard let db = db else {
            log.error("\(Constants.noDatabaseConnection)")
            return nil
        }
        
        do {
            let filteredMessages = messagesTable.filter(msgId == recordId)
            
            for row in try db.prepare(filteredMessages) {
                return recordId == row[self.msgId]
            }
        } catch {
            log.error("Error searching for message in DB: \(error)")
        }

        return false
    }

    func getMessage(recordId: String, completion: @escaping (ChatMessage?) -> Void) {
        guard let db = db else {
            log.error("\(Constants.noDatabaseConnection)")
            return
        }
        
        do {
            let filteredMessages = messagesTable.filter(msgId == recordId)
            
            for row in try db.prepare(filteredMessages) {
                        
                var msg = ChatMessage()
                
                msg.id = row[self.msgId]
                msg.chatId = row[self.msgChatId]
                msg.senderId = row[self.msgSenderId]
                msg.receiverId = row[self.msgRecieverId]
                msg.imageUrl = row[self.msgImageUrl]
                msg.msg = row[self.msgMessage]
                msg.timestamp = Utils.dateFromString(row[self.chatTimestamp] ?? "")?.millisecondsSince1970

                return completion(msg)
            }
        } catch {
            log.error("Error retrieving message from DB: \(error)")
        }

        completion(nil)
    }
 
    func saveMessage(chatMsgId: String?,
                     senderId: String,
                     msg: String,
                     direction: ChatDirection,
                     chat: Chat,
                     completion: @escaping (String?) -> Void) {
        
        do {
            guard let db = db else {
                log.warning("\(Constants.noDatabaseConnection)")
                return
            }
            
            guard let chatId = chat.id else {
                log.warning("Cannot save chat message because chatId or senderId is nil")
                return
            }
            
            guard var receiverId = chat.participantIds?.last else {
                log.warning("Message has no receiver uri")
                return
            }
            
            if receiverId == senderId {
                guard let first = chat.participantIds?.first else {
                    log.warning("Message has no receiver uri")
                    return
                }
                
                receiverId = first
            }
            
            let id = chatMsgId ?? UUID().uuidString
            
            let now = Date.now
            
            let insert = messagesTable.insert(self.msgId <- id,
                                              self.msgChatId <- chatId,
                                              self.msgSendOrRcvd <- direction.rawValue,
                                              self.msgSenderId <- senderId,
                                              self.msgRecieverId <- receiverId,
                                              self.msgImageUrl <- nil,
                                              self.msgMessage <- msg,
                                              self.msgTimestamp <- Utils.dateToString(now))
            
            _ = try db.run(insert)
            
            log.info("Successfully inserted chat message, recordId: \(id)")
            
            updateChat(id: chatId, lastMessage: msg)
            
            return completion(id)
        } catch {
            log.error("Error saving chat message: \(error)")
        }

        completion(nil)
    }
    
    func savePhotoMessage(senderUri: String, image: UIImage, chat: Chat) {
        
    }
}

extension SQLiteDBService : TypeNameDescribable {}
