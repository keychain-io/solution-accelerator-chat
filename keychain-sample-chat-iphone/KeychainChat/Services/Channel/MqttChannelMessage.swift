//
//  MqttChannelMessage.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/11/26.
//

import Foundation

public struct MqttChannelMessage {
    var destination: String
    var message: String
    
    init(destination: String, message: String) {
        self.destination = destination
        self.message = message
    }
}
