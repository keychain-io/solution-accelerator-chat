//
//  MqttChannel.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/11/26.
//

import Foundation

import Logging

public class MqttChannel: Channel {
    let log = Logger(label: MqttChannel.typeName)
    
    var mqttService = MqttService.shared()
    var topics = Set<String>()
    var messageHandler: MqttMessageHandler
    
    var pairingChannel: String
    var transferChannel: String
    
    var monitorLock = NSLock()
    
    init(pairingChannel: String,
         transferChannel: String,
         messageHandler: MqttMessageHandler) {
        
        log.info("Opening MQTT Channel")
        
        self.pairingChannel = pairingChannel
        self.transferChannel = transferChannel
        self.messageHandler = messageHandler
    }
    
    func subscribe(subscriptions: [String]) {
        topics = Set<String>()
        
        for subscription in subscriptions {
            topics.insert(subscription)
            mqttService.subscribe(topic: subscription)
        }
    }
   
    public func send(destination: String, message: String) {
        mqttService.publish(destination: destination, message: message)
    }
    
    public func didReceive(source: String, message: String) {
        log.info("Received message from source: \(source)")
        messageHandler.didReceiveMqttMessage(source: source, message: message)
    }
    
    func didStatusChange(_ status: MQTTConnectionState) {
        if status.isConnected {
            messageHandler.didConnectMqtt()
        } else if status == .disconnected {
            messageHandler.didDisconnectMqtt()
        }
    }
    
    public func close() {
        for topic in topics {	
            mqttService.unSubscribe(topic: topic)
        }
        
        mqttService.disconnect()
    }
}

extension MqttChannel : TypeNameDescribable {}
