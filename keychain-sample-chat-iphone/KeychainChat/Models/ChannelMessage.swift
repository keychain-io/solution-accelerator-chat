//
//  ChannelMessage.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/10.
//

import Foundation

import Logging

class ChannelMessage {
    static let log = Logger(label: ChannelMessage.typeName)
    
    static func makePairAck(me: Persona, respondTo: PairingMessage) -> PairingMessage? {
        do {
            guard respondTo.msgType == .pairResponse else {
                return nil
            }
            
            let senderUri = try me.getUri().toString()
            
            return try PairingMessage(msgType: .pairAck,
                                      receiverId: respondTo.senderId,
                                      senderId: senderUri,
                                      senderName: me.getName(),
                                      senderSubName: me.getSubName())
        } catch {
            log.error("Error creating pair ack: \(error)")
            return nil
        }
    }

    static func makePairResponse(me: Persona, respondTo: PairingMessage) -> PairingMessage? {
        do {
            guard respondTo.msgType == .pairRequest else {
                return nil
            }
            
            let senderUri = try me.getUri().toString()
            
            return try PairingMessage(msgType: .pairResponse,
                                      receiverId: respondTo.senderId,
                                      senderId: senderUri,
                                      senderName: me.getName(),
                                      senderSubName: me.getSubName())
        } catch {
            log.error("Error creating pair response: \(error)")
            return nil
        }
    }
    
    static func makePairRequest(me: Persona, requestUri: String, overrideSubName: String?) -> PairingMessage? {
        do {
            let senderUri = try me.getUri().toString()
            
            return try PairingMessage(msgType: .pairRequest,
                                      receiverId: requestUri,
                                      senderId: senderUri,
                                      senderName: me.getName(),
                                      senderSubName: overrideSubName == nil ? me.getSubName() : overrideSubName!)
            
        } catch {
            log.error("Error creating pair request: \(error)")
            return nil
        }
    }
}

extension ChannelMessage : TypeNameDescribable {}
