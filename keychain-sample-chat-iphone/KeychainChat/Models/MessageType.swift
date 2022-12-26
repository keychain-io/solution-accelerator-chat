//
//  PairMessageType.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/10.
//

import Foundation

enum MessageType: String, Codable {
    case pairRequest = "pairRequest"
    case pairResponse = "pairResponse"
    case pairAck = "pairAck"
    case chatMessage = "chatMessage"
}
