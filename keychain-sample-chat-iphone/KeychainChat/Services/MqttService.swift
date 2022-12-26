//
//  MqttService.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/11/24.
//

import Foundation

import CocoaMQTT
import Combine

import Logging

final class MqttService: ObservableObject {
    
    let log = Logger(label: MqttService.typeName)
    
    private var mqttClient: CocoaMQTT?
    private var identifier: String!
    private var host: String!
    private var port: Int!
    private var topic: String!
    private var username: String!
    private var password: String!
    private var channel: MqttChannel?

    @Published var currentAppState = MQTTAppState()
    
    private var anyCancellable: AnyCancellable?
    
    // Private Init
    private init() {
        // Workaround to support nested Observables, without this code changes to state is not propagated
        anyCancellable = currentAppState.objectWillChange.sink { [weak self] _ in
            self?.objectWillChange.send()
        }
    }

    // MARK: Shared Instance

    private static let _shared = MqttService()

    // MARK: - Accessors

    class func shared() -> MqttService {
        return _shared
    }

    func initializeMQTT(host: String, port: Int, identifier: String,
                        username: String? = nil, password: String? = nil,
                        channel: MqttChannel) {
        
        // --- If any previous instance exists then clean it ---
        if self.channel != nil {
            close()
        }
        
        if mqttClient != nil {
            mqttClient = nil
        }
        // -----------------------------------------------------
        
        self.channel = channel
        self.identifier = identifier
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        let clientID = "CocoaMQTT-\(identifier)-" + String(ProcessInfo().processIdentifier)

        // TODO: Guard
        mqttClient = CocoaMQTT(clientID: clientID, host: host, port: UInt16(port))
        
        // If a server has username and password, pass it here
        if let finalusername = self.username, let finalpassword = self.password {
            mqttClient?.username = finalusername
            mqttClient?.password = finalpassword
        }
        
        //mqttClient?.willMessage = CocoaMQTTWill(topic: "/will", message: "dieout")
        mqttClient?.allowUntrustCACertificate = true
        mqttClient?.autoReconnect = true
        mqttClient?.backgroundOnSocket = true
        mqttClient?.logLevel = .debug
        mqttClient?.keepAlive = 360
        mqttClient?.delegate = self
        
        log.info("Connecting to MQTT with clientId: \(clientID)")
        
        connect()
    }

    func connect() {
        if let success = mqttClient?.connect(), success {
            currentAppState.setConnectionState(state: .connecting)
        } else {
            currentAppState.setConnectionState(state: .disconnected)
        }
    }
    
    func subscribe(subscriptions: [String]) {
        guard let channel = channel else {
            log.warning("Cannot subscribe to topics, channel is nil")
            return
        }

        channel.subscribe(subscriptions: subscriptions)
    }

    func subscribe(topic: String) {
        self.topic = topic
        mqttClient?.subscribe(topic, qos: .qos2)
    }

    func publish(destination: String, message: String) {
        log.info("Publishing message to: \(destination)")
        
        mqttClient?.publish(CocoaMQTTMessage(topic: destination,
                                             payload: message.utf8Bytes,
                                             qos: .qos2,
                                             retained: false))
    }

    func send(destination: String, message: String) throws {
        guard let channel = channel else {
            throw KeychainError.runtimeError("MQTT channel is nil")
        }
        
        DispatchQueue.global().async {
            channel.send(destination: destination, message: message)
        }
    }
    
    func close() {
        if let channel = channel {
            log.info("Closing MQTT Channel")
            channel.close()
        }
    }

    func disconnect() {
        mqttClient?.disconnect()
    }

    /// Unsubscribe from a topic
    func unSubscribe(topic: String) {
        mqttClient?.unsubscribe(topic)
    }

    /// Unsubscribe from a topic
    func unSubscribeFromCurrentTopic() {
        mqttClient?.unsubscribe(topic)
    }
    
    func currentHost() -> String? {
        return host
    }
    
    func isSubscribed() -> Bool {
       return currentAppState.connectionState.isSubscribed
    }
    
    func isConnected() -> Bool {
        return self.channel != nil && currentAppState.connectionState.isConnected
    }
    
    func connectionStateMessage() -> String {
        return currentAppState.connectionState.description
    }
}

extension MqttService: CocoaMQTTDelegate {
    func mqtt(_ mqtt: CocoaMQTT, didSubscribeTopics success: NSDictionary, failed: [String]) {
        log.info("Subscribe topic: \(success)")
        setStatus(.connectedSubscribed)
    }

    func mqtt(_ mqtt: CocoaMQTT, didUnsubscribeTopics topics: [String]) {
        log.info("Unsubscribe topic: \(topics)")
        setStatus(.connectedUnSubscribed)
    }
    
    func mqtt(_ mqtt: CocoaMQTT, didConnectAck ack: CocoaMQTTConnAck) {
        log.info("Connect Ack: \(ack)")

        if ack == .accept {
            setStatus(.connected)
        }
    }

    func mqtt(_ mqtt: CocoaMQTT, didPublishMessage message: CocoaMQTTMessage, id: UInt16) {
        log.info("Published message: \(message.string.description), id: \(id)")
    }

    func mqtt(_ mqtt: CocoaMQTT, didPublishAck id: UInt16) {
        log.info("Published Ack id: \(id)")
    }

    func mqtt(_ mqtt: CocoaMQTT, didReceiveMessage message: CocoaMQTTMessage, id: UInt16) {
        log.info("Received message: \(message.string.description), id: \(id)")
        
        currentAppState.setReceivedMessage(text: message.string.description)
        
        if message.duplicated {
            log.warning("Received duplicate MQTT message, should be ignored.")
            return
        }
        
        if message.payload.count == 0 {
            log.warning("Received MQTT message with no payload, should be ignored.")
            return
        }

        guard let channel = channel else {
            log.error("Received message from MQTT, but the channel is nil.")
            return
        }
        
        channel.didReceive(source: message.topic, message: String(decoding: message.payload, as: UTF8.self))
    }

    func mqtt(_ mqtt: CocoaMQTT, didUnsubscribeTopic topic: String) {
        log.info("Unsubscribed from topic: \(topic)")
        
        setStatus(.connectedUnSubscribed)
        currentAppState.clearData()
    }

    func mqttDidPing(_ mqtt: CocoaMQTT) {
        log.info("MQTT Ping")
    }

    func mqttDidReceivePong(_ mqtt: CocoaMQTT) {
        log.info("MQTT Pong")
    }

    func mqttDidDisconnect(_ mqtt: CocoaMQTT, withError err: Error?) {
        if err == nil {
            log.info("Disconnected mqttClient gracefully.")
        } else {
            log.error("Mqtt disconnect failed with error: \(err.description)")
        }
        
        setStatus(.disconnected)
    }
    
    func setStatus(_ status: MQTTConnectionState) {
        currentAppState.setConnectionState(state: status)
        
        guard let channel = channel else {
            log.warning("Cannot update MQTT status, channel is nil")
            return
        }
        
        channel.didStatusChange(status)
    }
}

extension MqttService : TypeNameDescribable {}

extension Optional {
    // Unwrap optional value for printing log only
    var description: String {
        if let wraped = self {
            return "\(wraped)"
        }
        return ""
    }
}
