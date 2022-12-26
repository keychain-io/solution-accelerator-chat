//
//  Config.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/08.
//

import Foundation

struct Config: Decodable {
    let mqttHost: String
    let mqttPort: Int
    let mqttChannelPairing : String
    let mqttChannelChats: String
    let directoryHost: String
    let directoryPort: Int
    let directoryDomainPrefix: String
    let pairingDomain: String
    let refreshInterval: TimeInterval
    let resetChatDB: Bool
}
