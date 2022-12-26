//
//  DBService.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/06/10.
//

import Foundation
import SwiftUI

protocol ChatRepository {
    func createOrOpenDB() throws
    
    func getPlatformUsers(filterBy: [String: Facade], completion: @escaping ([String: User]) -> Void)
    
    func getPlatformUser(recordId: String, completion: @escaping (User?) -> Void)
    
    func getPlatformUser(firstName: String, lastName: String, completion: @escaping (User?) -> Void)
    
    func getPlatformUser(uri: String, completion: @escaping (User?) -> Void)
    
    func saveUserProfile(firstName: String, lastName: String, status: Int, uri: String?, image: UIImage?, completion: @escaping (String?) -> Void)
    
    func updateUserProfile(uri: String, firstName: String, lastName: String?, completion: @escaping (String?) -> Void)
    
    func updateUserProfile(firstName: String, lastName: String, status: Int, uri: String?, completion: @escaping (String?) -> Void)
    
    func deleteUser(uri: String, completion: @escaping (String?) -> Void)
    
    func getAllChats(senderId: String, completion: @escaping ([Chat]) -> Void)
    
    func getChat(senderId: String, receiverId: String, completion: @escaping (Chat?) -> Void)
    
    func getAllMessages(chat: Chat, completion: @escaping ([ChatMessage]) -> Void)
    
    func saveChat(chat: Chat, completion: @escaping (String?) -> Void)
    
    func updateChat(id: String, lastMessage: String)
    
    func isMessageExisting(recordId: String) -> Bool?
    
    func getMessage(recordId: String, completion: @escaping (ChatMessage?) -> Void)

    func saveMessage(chatMsgId: String?,
                     senderId: String,
                     msg: String,
                     direction: ChatDirection,
                     chat: Chat, completion: @escaping (String?) -> Void)
    
    func savePhotoMessage(senderUri: String, image: UIImage, chat: Chat)
}
