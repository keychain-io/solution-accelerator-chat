//
//  MQTTAppState.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/11/24.
//

import Foundation

final class MQTTAppState: ObservableObject {
    @Published var connectionState: MQTTConnectionState = .disconnected
    @Published var historyText: String = ""
    
    private var receivedMessage: String = ""

    func setReceivedMessage(text: String) {
        receivedMessage = text
        historyText = historyText + "\n" + receivedMessage
    }

    func clearData() {
        receivedMessage = ""
        historyText = ""
    }

    func setConnectionState(state: MQTTConnectionState) {
        connectionState = state
    }
}
