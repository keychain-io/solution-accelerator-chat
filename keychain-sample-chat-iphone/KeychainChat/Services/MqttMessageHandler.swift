//
//  MqttMessageHandler.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/11.
//

import Foundation

protocol MqttMessageHandler {
    func didConnectMqtt()
    
    func didDisconnectMqtt()
    
    func didReceiveMqttMessage(source: String, message: String)
}
