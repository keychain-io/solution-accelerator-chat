//
//  Channel.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/11/26.
//

import Foundation

import CocoaMQTT
import Combine

protocol Channel {
    func send(destination: String, message: String)

    func didReceive(source: String, message: String)

    func didStatusChange(_ status: MQTTConnectionState)
    
    func close()
}
