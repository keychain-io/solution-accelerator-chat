//
//  ChatMessage.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/05/31.
//

import Foundation

struct ChatMessage: Codable, Identifiable, Hashable {
    var id: String?
        
    var chatId: String?
    
    var sendOrRcvd: ChatDirection?

    var senderId: String?

    var receiverId: String?

    var imageUrl: String?
    
    var msg: String?
    
    var timestamp: Int64?
}
