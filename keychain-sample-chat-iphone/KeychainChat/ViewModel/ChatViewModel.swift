//
//  TransactionsViewModel.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 9/5/21.
//

import Foundation

import UIKit
import SwiftUI

import Logging

class ChatViewModel: ObservableObject,
                     Refreshable,
                     MqttMessageHandler,
                     ViewAction,
                     LoginListener{
    let log = Logger(label: ChatViewModel.typeName)
    
    let config = AppConfig.get()
    
    var mqttService = MqttService.shared()
    var isMqttAlreadyConnected = false
    
    @Published var persona: Persona?
    @Published var contacts = [Contact]()
    
    @Published var chats = [Chat]()
    @Published var selectedChat: Chat?
    @Published var messages = [ChatMessage]()
    
    @EnvironmentObject var loginViewModel: AuthViewModel
    var contactViewModel: ContactViewModel?

    @Published var notificationMessage = ""
    
    @State var isShowingSendToQRCode = false
    @State var isShowingMyQRCode = false
    @State var isChatShowing = false
    
    var keychainService = KeychainService.instance
    
    var chatsDict = [String: Chat]()
    
    let refreshLock = NSLock()
    
    let contactsLock = NSLock()
    
    var pairHelper: PairHelper?
    var pairedPersonas: Dictionary<String, Persona> = Dictionary()
    
    init() {
        // Retrieve chats when ChatViewModel is created
        pairHelper = PairHelper(host: config.directoryHost,
                                port: config.directoryPort,
                                domain: "\(config.directoryDomainPrefix)\(config.pairingDomain)")
        
        keychainService?.resumeMonitor()
    }
    
    func reset() {
        setActivePersona(persona: nil)
        contacts.removeAll()
        chats = [Chat]()
        messages = [ChatMessage]()
        selectedChat = nil
    }
    
    func onRefresh() {
        DispatchQueue.global(qos: .userInteractive).async { [self] in
            if (!refreshLock.try()) {
                return
            }
            
            defer {
                refreshLock.unlock()
            }
                        
            DispatchQueue.main.async { [self] in
                if !mqttService.isConnected() {
                    //closeMqttChannel()
                    openMqttChannel()
                }
            }
        }
    }
    
    func onLogin() {
        onViewAppeared()

        setActivePersona(persona: keychainService?.getActivePersona())
        
        guard persona != nil else {
            log.error("Unable to set active persona.")
            return
        }

        getChats()
        
        DispatchQueue.global(qos: .userInteractive).async { [self] in
            do {
                if let pairHelper = pairHelper {
                    guard let uriString = try persona?.getUri().toString() else {
                        return
                    }
                    
                    if !pairedPersonas.keys.contains(uriString) {
                        if try UriUploader.doUpload(uri: persona?.getUri(), pairHelper: pairHelper) {
                            pairedPersonas[uriString] = persona
                        }
                    }
                }
            } catch {
                log.error("Error uploading persona uri: \(error)")
            }
        }
    }
    
    func onLogoff() {
        onViewDisappeared()
        reset()
    }
    
    func onViewAppeared() {
        keychainService?.registerRefreshListener(name: typeName, refreshable: self)
        onRefresh()
    }
    
    func onViewDisappeared() {
        keychainService?.unregisterRefreshListener(typeName)
    }
    
    func setActivePersona(persona: Persona?) {
        DispatchQueue.main.async { [self] in
            guard let p = persona else {
                return
            }
            
            do {
                if try p.isMature().boolValue {
                    let _ = keychainService?.setActivePersona(p);
                    self.persona = p
                    return
                }
            } catch  {
                log.warning("Error setting active persona: \(error)");
            }
            
            // set to nil so observers get a callback to handle
            self.persona = nil
        }
    }
    
    func getActivePersona() -> Persona? {
        return keychainService?.getActivePersona()
    }
    
    func getSenderOrReceiverName(transaction: LedgerTransaction, personaUri: String) -> Text {
        do {
            let senderUri = try transaction.getSenderUrl()
            let receiverUri = try transaction.getReceiverUrl()
            let sender = keychainService?.findContact(senderUri)
            let receiver = keychainService?.findContact(receiverUri)
            
            if senderUri == personaUri {
                return Text(getName(fasade: receiver))
            } else if receiverUri == personaUri {
                return Text(getName(fasade: sender))
            }
        } catch {
            log.error("Error getting receiver or sender name from transaction. \(error)")
        }
        
        return Text("")
    }
    
    func getName(fasade: Facade?) -> String {
        do {
            if let fasade = fasade {
                return try "\(fasade.getName()) \(fasade.getSubName())"
            }
        } catch {
            log.error("Error getting name: \(error)")
        }
        
        return ""
    }
    
    func getSenderOrReceiverUri(transaction: LedgerTransaction, personaUri: String) -> Text {
        do {
            let senderUri = try transaction.getSenderUrl()
            let receiverUri = try transaction.getReceiverUrl()
            
            if senderUri == personaUri {
                return Text(senderUri)
            } else if receiverUri == personaUri {
                return Text(receiverUri)
            }
        } catch {
            log.error("Error getting receiver or sender uri from transaction. \(error)")
        }
        
        return Text("")
    }
    
    func getAcceptanceStateTextColor(_ status: Int64) -> Color {
        if (status < 0) {
            return .red
        }
        
        if (status == 0) {
            return .gray
        }
        
        return .green
        
    }
    
    public func loadContacts() {
        DispatchQueue.main.async { [self] in
            if !contactsLock.try() {
                return
            }
            
            defer {
                contactsLock.unlock()
            }
            
            contacts = keychainService?.getContacts() ?? []
            contactViewModel?.getChatContacts()
            log.info("Loaded \(contacts.count) contacts.")
        }
    }
    
    func pairUsingTrustedDirectory() {
        log.info("Initiating pairing using the trusted directory")
        
        DispatchQueue.global(qos: .userInteractive).async { [self] in
            guard let pairHelper = pairHelper else {
                log.error("PairHelper is nil")
                return
            }
            
            pairHelper.getUrisFromTrustedDirectory(completion: { [self]uris in
                do {
                    guard let myUri = try getActivePersona()?.getUri().toString() else {
                        log.error("\(Constants.noActivePersona)")
                        return
                    }
                    
                    for uri in uris {
                        guard myUri != uri else {
                            // Don't pair with yourself
                            continue
                        }
                        
                        self.pair(uri: uri)
                    }
                } catch {
                    log.error("\(Constants.somethingWentWrong): \(error)")
                }
            })
        }
        
    }
    
    func pair(uri: String, overrideSubName: String?) {
        log.info("Sending pairing request for URI \(uri)")
        
        guard let persona = keychainService?.activePersona else {
            log.error("Unable to pair, no active persona exists")
            return
        }
        
        guard let payload = ChannelMessage.makePairRequest(me: persona,
                                                           requestUri: uri,
                                                           overrideSubName: overrideSubName)?.convertToJsonString else {
            log.error("Unable to pair")
            return
        }
        
        let topic = "\(config.mqttChannelPairing)\(uri)"
        log.info("Sending pairing request to topic: \(topic)")
        log.info("\(payload)")
        
        sendToMqtt(topic: topic, payload: payload)
    }
    
    func pair(uri: String) {
        pair(uri: uri, overrideSubName: nil)
    }
    
    func sendToMqtt(topic: String, payload: String) {
        do {
            guard keychainService?.activePersona != nil else {
                log.error("\(Constants.noActivePersona)")
                return
            }
            
            try mqttService.send(destination: topic, message: payload)
        } catch {
            log.error("\(Constants.somethingWentWrong) \(error)")
        }
    }
    
    func openMqttChannel() {
        mqttService.initializeMQTT(host: config.mqttHost,
                                   port: config.mqttPort,
                                   identifier: "keychainChat-iphone",
                                   username: nil,
                                   password: nil,
                                   channel: MqttChannel(pairingChannel: config.mqttChannelPairing,
                                                        transferChannel: config.mqttChannelChats,
                                                        messageHandler: self))
    }
    
    func closeMqttChannel() {
        mqttService.close()
    }
    
    func subscribeToMQTTTopic(_ activePersona: Persona) {
        do {
            let uri = try activePersona.getUri().toString()
            
            // Subscribe to pairing, my chats, and all chats channels
            let topics = ["\(config.mqttChannelPairing)\(uri)",
                          "\(config.mqttChannelChats)\(uri)",
                          "\(config.mqttChannelChats)\(Constants.all)"]
            
            mqttService.subscribe(subscriptions: topics)
        } catch {
            log.error("\(Constants.somethingWentWrong) \(error)")
        }
    }
    
    func didConnectMqtt() {
        if isMqttAlreadyConnected {
            log.warning("MQTT is already connected")
            return
        }
        
        isMqttAlreadyConnected = true
        
        guard let activePersona: Persona = keychainService?.activePersona else {
            log.error("Unable to subscribe to topics. Error getting active persona URI in openMqttChannel.")
            return
        }
        
        subscribeToMQTTTopic(activePersona)
    }
    
    func didDisconnectMqtt() {
        isMqttAlreadyConnected = false
    }
    
    
    func isMqttConnected() -> Bool {
        return mqttService.isConnected()
    }
    
    func didReceiveMqttMessage(source: String, message: String) {
        DispatchQueue.main.async { [self] in
            do {
                if source.hasPrefix(config.mqttChannelPairing) {
                    log.info("Received pairing message: \(message)")
                    
                    guard let pairingMessage = JSONUtils.fromJson(PairingMessage.self, json: message) else {
                        log.error("Unable to parse pairing message.")
                        return
                    }
                    
                    guard let persona = keychainService?.activePersona else {
                        log.error("\(Constants.noActivePersona)")
                        return
                    }
                    
                    guard try persona.getUri().toString() == pairingMessage.receiverId else {
                        log.warning("Received \(pairingMessage.msgType). But it is not for me. Ignoring it!")
                        return
                    }
                    
                    switch (pairingMessage.msgType) {
                        
                    case .pairRequest:
                        log.info("Received pair request.")
                        
                        guard let response = ChannelMessage.makePairResponse(me: persona, respondTo: pairingMessage) else {
                            log.error("\(Constants.errorMakingPairingResponse)")
                            return
                        }
                        
                        guard let response = JSONUtils.toJson(target: response) else {
                            log.error("Error converting pairing response to JSON.")
                            return
                        }
                        
                        log.info("Sending pair response")
                        log.info("\(response)")

                        sendToMqtt(
                            topic: "\(config.mqttChannelPairing)\(pairingMessage.senderId)",
                            payload: response)
                        
                        isShowingMyQRCode = false
                        
                    case .pairResponse, .pairAck:
                        log.info("Received pair \(pairingMessage.msgType)")
                        
                        var contact = keychainService?.findContact(pairingMessage.senderId)
                        
                        if contact == nil {
                            // I received a response to my request, so add the responder to my contact list
                            if keychainService?.findPersona(pairingMessage.senderId) != nil {
                                // Don't pair with any of your own personas
                                break;
                            }
                            
                            let contactUri: Uri = try Uri.withUriString(pairingMessage.senderId)
                            contact = keychainService?.createContact(name:  pairingMessage.senderName,
                                                                    subName: pairingMessage.senderSubName,
                                                                         uri: contactUri)
                            
                            if contact == nil {
                                log.error("Erro pairing with contact: \(contactUri)")
                                break;
                            }
                        }
                        
                        keychainService?.chatRepository.saveUserProfile(firstName: pairingMessage.senderName,
                                                                        lastName: pairingMessage.senderSubName,
                                                                        status: PersonaStatus.confirmed.rawValue,
                                                                        uri: pairingMessage.senderId,
                                                                        image: nil,
                                                                        completion: { [self] recordId in
                            
                            loadContacts()
                            
                            log.info("Successfully saved paired contact to chat user table for: \(pairingMessage.senderName) \(pairingMessage.senderSubName)")
                        })
                        
                        if (pairingMessage.msgType == MessageType.pairResponse) {
                            guard let response = ChannelMessage.makePairAck(me: persona, respondTo: pairingMessage) else {
                                log.error("\(Constants.errorMakingPairingAck)")
                                return
                            }
                            
                            guard let json = JSONUtils.toJson(target: response) else {
                                log.error("Error converting pairing ack to JSON.")
                                return
                            }
                            
                            log.info("Sending pair ack")
                            log.info("\(response)")

                            sendToMqtt(
                                topic: "\(config.mqttChannelPairing)\(response.receiverId)",
                                payload: json)
                        }
                        
                    case .chatMessage:
                        break
                    }
                } else if source.hasPrefix(config.mqttChannelChats) {
                    log.info("Receiving chat message: \(message)")
                    
                    guard var chatMessage = JSONUtils.fromJson(ChatMessage.self, json: message) else {
                        log.error("Unable to parse chat message")
                        return
                    }
                    
                    guard let messageId = chatMessage.id else {
                        log.error("\(Constants.somethingWentWrong) Chat message has no message id.")
                        return
                    }
                    
                    guard let exists = keychainService?
                        .chatRepository
                        .isMessageExisting(recordId: messageId) else {
                        
                        // There was an error searching the db
                        return;
                    }
                    
                    guard !exists else {
                        // We already processed the message
                        return
                    }
                    
                    chatMessage.msg = chatMessage.msg?.fromBase64() ?? chatMessage.msg
                    
                    guard let myId = try getActivePersona()?.getUri().toString() else {
                        log.error("Cannot get chat. No active persona.")
                        return
                    }
                    
                    guard let senderId = chatMessage.senderId else {
                        log.error("No sender id in chat message.")
                        return
                    }
                    
                    guard myId != senderId else {
                        // Received my own message, which was sent to all. Ignore it.
                        return
                    }
                    
                    guard keychainService?.findContact(senderId) != nil else {
                        log.warning("Message sender is not in my contact list. Ignoring message.")
                        return
                    }
                    
                    guard let receiverId = chatMessage.receiverId else {
                        log.error("No receiver id in chat message")
                        return
                    }
                    
                    if (selectedChat?.participantIds?.first == senderId &&
                        selectedChat?.participantIds?.last == receiverId) ||
                        (selectedChat?.participantIds?.first == receiverId &&
                         selectedChat?.participantIds?.last == senderId) {
                        
                        saveMessage(existingChat: selectedChat,
                                    receiverId: receiverId,
                                    senderId: senderId,
                                    chatMessage: chatMessage)
                    } else {
                        keychainService?.chatRepository.getChat(senderId: senderId, receiverId: receiverId, completion: { [self] existingChat in
                            
                            saveMessage(existingChat: existingChat,
                                        receiverId: receiverId,
                                        senderId: senderId,
                                        chatMessage: chatMessage)
                        })
                    }
                }
            } catch {
                log.error("\(Constants.somethingWentWrong): \(error)")
            }
        }
    }
    
    func saveMessage(existingChat: Chat?,
                     receiverId: String,
                     senderId: String,
                     chatMessage: ChatMessage) {
        
        var chat: Chat?
        
        if existingChat == nil {
            let receiverId = receiverId == Constants.all ? Constants.all : receiverId
            
            chat = Chat()
            chat?.id = UUID().uuidString
            chat?.participantIds = [senderId, receiverId]
        } else {
            chat = existingChat
        }
        
        chat?.lastMsg = chatMessage.msg
        chat?.timestamp = Date.now
        
        keychainService?.chatRepository.saveChat(chat: chat!, completion: { [self] recordId in
            if let recordId = recordId {
                log.info("Chat saved or updated to chat database, recordId: \(recordId)")
            } else {
                log.error("Error saving or updating chat from sender: \(senderId)")
                chat = nil
            }
        })
        
        guard let chat = chat else {
            log.error("Error processing chat received from senderId: \(senderId)")
            return
        }
        
        guard var encryptedMessage = chatMessage.msg else {
            log.error("No message received in chat message from: \(senderId)")
            return
        }
        
        if encryptedMessage.starts(with: Constants.xmlStartEncoded) {
            encryptedMessage = encryptedMessage.fromBase64() ?? encryptedMessage
        }
        
        keychainService?.chatRepository.saveMessage(chatMsgId: chatMessage.id,
                                                    senderId: senderId,
                                                    msg: encryptedMessage,
                                                    direction: ChatDirection.receive,
                                                    chat: chat,
                                                    completion: { [self] recordId in
            
            guard recordId != nil else {
                return
            }
            
            getMessages()
        })
    }
    
    fileprivate func decrypteMessage(_ message: String) -> String {
        if let msg = keychainService?.decryptThenVerify(message: message) {
            return msg
        }
        
        return message
    }
    
    func getDecryptedMessage(_ message: String) -> String {
        if StringUtils.isXML(text: message) {
            let decryptedMessage = decrypteMessage(message)
            return decryptedMessage
        } else if (message.starts(with: "%3C")) {
            let msg = (message.removingPercentEncoding  ?? message).replacingOccurrences(of: "+", with: " ")
            return decrypteMessage(msg)
        } else if let msg = message.fromBase64() {
            return decrypteMessage(msg)
        }
        
        return message
    }
    
    func pairingHandler(_ message: PairingMessage) -> Contact? {
        let contactId = message.senderId
        let contactName = message.senderName
        var contactSubName = message.senderSubName
        
        var rc: Contact?
        
        do {
            if message.msgType == .pairResponse {
                guard let activePersona = keychainService?.getActivePersona() else {
                    log.error("Cannot pair because there is no actibe persona.")
                    return nil
                }
                
                let activePersonaUri = try activePersona.getUri().toString()
                
                if activePersonaUri != message.receiverId {
                    log.error("Target persona for pairing response not found. Aborting pairing handshake")
                    return nil
                }
            }
            
            rc = keychainService?.findContact(contactId)
            
            if (rc == nil) {
                // Search for the contact and add if necessary, returning false if failure
                let existingContacts = contacts
                
                // If name already taken, append random number
                for contact in existingContacts {
                    if try contactName == contact.getName() &&
                        contactSubName == contact.getSubName() {
                        
                        contactSubName.append(" ")
                        contactSubName.append(String(Int.random(in: 0...999)))
                    }
                }
                
                log.info("Adding contact (URI): \(contactId)")
                
                rc = keychainService?.createContact(name: contactName,
                                                    subName: contactSubName,
                                                    uri: try Uri.withUriString(contactId))
                
                if (rc == nil) {
                    log.warning("Contact creation failed")
                    return nil
                }
            }
            
            keychainService?.chatRepository.getPlatformUser(uri: message.senderId, completion: { [self] user in
                if user == nil {
                    keychainService?.chatRepository.saveUserProfile(firstName: message.senderName,
                                                                    lastName: message.senderSubName,
                                                                    status: PersonaStatus.confirmed.rawValue,
                                                                    uri: message.senderId,
                                                                    image: nil,
                                                                    completion: { [self] recordId in
                        
                        log.info("Successfully saved paired contact to chat user table for: \(message.senderName) \(message.senderSubName)")
                    })
                }
            })
            
            loadContacts()
            
            return rc
        } catch {
            log.error("Error handling pairing")
            return nil
        }
    }
    
    fileprivate func updateChatsDictionary(_ theChats: [Chat]) {
        chatsDict = theChats.reduce(into: [String: Chat]()) {
            if let id = $1.id {
                $0[id] = $1
            }
        }
    }
    
    func getChats() {
        do {
            guard let uri = try persona?.getUri().toString()  else {
                log.error("\(Constants.somethingWentWrong): No active persona.")
                return
            }
            
            // Use the database service to retrieve the chats
            keychainService?.chatRepository.getAllChats(senderId: uri) { [self] theChats in
                var needsUpdate = false
                
                if theChats.count != chatsDict.keys.count {
                    needsUpdate = true
                    updateChatsDictionary(theChats)
                } else {
                    for chat in theChats {
                        guard let id = chat.id else {
                            continue
                        }
                        
                        // If chat not found, need to update UI
                        guard let c: Chat = chatsDict[id] else {
                            chatsDict[id] = chat
                            needsUpdate = true
                            continue
                        }
                        
                        // If last message changed, need to update UI
                        if c.lastMsg != chat.lastMsg {
                            chatsDict[id] = chat
                            needsUpdate = true
                        }
                    }
                }
                
                // Set the retrieved data to the chats property
                if needsUpdate {
                    DispatchQueue.main.async { [self] in
                        chats = theChats
                    }
                }
            }
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
        }
    }
    
    /// Search for chat with passed in user. If found, set as selected chat. If not found, create a new chat
    func getChatFor(senderUri: String, contact: User) {
        
        // Check the contact
        guard let contactUri = contact.uri else {
            return
        }
        
        let foundChat = chats.filter { chat in
            return StringUtils.arrayContains(array: chat.participantIds, element: contactUri)
        }
        
        // Found a chat between the user and the contact
        if !foundChat.isEmpty {
            // Set as selected chat
            self.selectedChat = foundChat.first!
            
            // Fetch the messages
            getMessages()
        }
        else {
            // No chat was found, create a new one
            let newChat = Chat(id: UUID().uuidString,
                               participantIds: [senderUri, contactUri],
                               lastMsg: nil,
                               timestamp: Date.now)
            
            // Set as selected chat
            self.selectedChat = newChat
            
            // Save new chat to the database
            keychainService?.chatRepository.saveChat(chat: newChat) { docId in
                
                // Add chat to the chat list
                self.chats.append(self.selectedChat!)
            }
        }
    }
    
    func getMessages() {
        
        // Check that there's a selected chat
        guard selectedChat != nil else {
            return
        }
        
        keychainService?.chatRepository.getAllMessages(chat: selectedChat!) { msgs in
            
            // Set returned messages to property
            self.messages = msgs
        }
    }
    
    func sendMessage(sender: String, receiver: String, msg: String) {
        DispatchQueue.global(qos: .userInteractive).async { [self] in
            log.info("Sending message to receiver")
            
            // Check that we have a selected chat
            guard selectedChat != nil else {
                log.warning("No selected chat.")
                return
            }
            
            var contacts = [Contact]()
            
            if receiver != Constants.all {
                // Sending to one contact
                guard let contact = keychainService?.findContact(receiver) else {
                    log.error("Unable to send message. Contact not found for recipient: \(receiver)")
                    return
                }
                
                contacts.append(contact)
            } else {
                // Send to all contacts
                contacts = keychainService?.getContacts() ?? [Contact]()
            }
            
            guard let encryptedMessage = keychainService?.signThenEncrypt(contacts: contacts, message: msg) else {
                log.error("\(Constants.somethingWentWrong): Failed to sign and encrypt message.")
                return
            }
            
            // Save message to chats database
            keychainService?.chatRepository.saveMessage(chatMsgId: nil,
                                                        senderId: sender,
                                                        msg: encryptedMessage,
                                                        direction: .send,
                                                        chat: selectedChat!,
                                                        completion: {[self] recordId in
                
                guard let recordId = recordId else {
                    log.error("Unable to send message. No recored id.")
                    return
                }
                
                DispatchQueue.main.async { [self] in
                    keychainService?.chatRepository.getMessage(recordId: recordId, completion: { [self] message in
                        guard let displayMessage = message else {
                            log.error("Unable to send chat message because it was not saved to the chat database.")
                            return
                        }
                        
                        var chatMessage = displayMessage
                        
                        chatMessage.msg = chatMessage.msg?.toBase64()
                        
                        guard let json = chatMessage.convertToJsonString else {
                            log.error("Unable to send chat message cannot convert ChatMessage to JSON.")
                            return
                        }
                        
                        let topic = "\(config.mqttChannelChats)\(receiver)"
                        log.info("Sending chat message to topic: \(topic)")
                        log.info("\(json)")
                        
                        // Send message to recipient(s)
                        sendToMqtt(topic: topic, payload: json)
                        
                        messages.append(displayMessage)
                    })
                }
            })
        }
    }
    
    func sendPhotoMessage(senderUri: String, recipientUri: String,image: UIImage) {
        // Check that we have a selected chat
        guard selectedChat != nil else {
            log.warning("No selected chat.")
            return
        }
        
        keychainService?.chatRepository.savePhotoMessage(senderUri: senderUri, image: image, chat: selectedChat!)
    }
}

extension ChatViewModel : TypeNameDescribable {}

