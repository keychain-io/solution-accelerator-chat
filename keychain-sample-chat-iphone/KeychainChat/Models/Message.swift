//
//  Message.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/06/17.
//

import Foundation

struct Message : Codable {
    var msgType: MessageType
    var chatMessage: ChatMessage
}
