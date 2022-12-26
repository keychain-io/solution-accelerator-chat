//
//  PairRequest.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/10.
//

import Foundation

struct PairingMessage : Codable {
    var msgType: MessageType
    var receiverId: String
    var senderId: String
    var senderName: String
    var senderSubName: String
}
