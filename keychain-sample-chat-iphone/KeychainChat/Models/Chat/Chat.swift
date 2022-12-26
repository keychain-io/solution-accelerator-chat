//
//  Chat.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/06/01.
//

import Foundation

struct Chat: Codable, Identifiable {
    
    var id: String?
    
    var participantIds: [String]?
    
    var lastMsg: String?
    
    var timestamp: Date?
}
