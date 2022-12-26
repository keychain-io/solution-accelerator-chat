//
//  Monitor.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/03.
//

import Foundation
import SwiftUI

import Logging

public class MonitorService {
    let log = Logger(label: MonitorService.typeName)
    
    var gateway: Gateway?
    var state = MonitorThreadState.stopped
    let stateLock = DispatchSemaphore(value: 1)
    let listenersLock = DispatchSemaphore(value: 1)
    
    var refreshInterval: TimeInterval = 1.0
    
    var listeners = [String : Refreshable]()
    
    public init(dbFilePath: String, gateway: Gateway?, refreshInterval: TimeInterval) throws {
        self.gateway = gateway
        
        self.refreshInterval = refreshInterval
    }
        
    public func addListener(name: String, listener: Refreshable) {
        listenersLock.wait()
        
        log.info("Adding listener \(name)")
        
        defer {
            listenersLock.signal()
        }
        
        listeners[name] = listener
    }
    
    public func removeListener(_ name: String) {
        listenersLock.wait()
        
        log.info("Removing listener \(name)")
        
        defer {
            listenersLock.signal()
        }
        
        listeners.removeValue(forKey: name)
    }
    
    fileprivate func removeAllListeners() {
        if !listeners.isEmpty {
            for (name, _) in listeners {
                log.info("Removing listeners: \(name)")
            }
            
            listeners.removeAll()
        }
    }
    
    public func setState(_ newState: MonitorThreadState) {
        do {
            if let monitor = self.gateway {
                defer {
                    stateLock.signal()
                }
                
                stateLock.wait()
                state = newState
                
                switch newState {
                        
                    case .started:
                    try monitor.onStart()
                        
                    case .resumed:
                        try monitor.onResume()
                        
                    case .paused:
                        try monitor.onPause()
                        
                    case .stopped:
                        try monitor.onStop()
                }
                
                log.info("Monitor thread state set to: \(newState)")
                
                if newState == .started {
                    // Start the monitor background thread
                    DispatchQueue.global(qos: .userInteractive).async {
                        self.runThread()
                    }
                }
            }
        } catch  {
            log.error("Error setting monitor state: \(error)")
        }
    }
    
    func runThread() {
        log.info("Monitor thread running")
        
        defer {
            stateLock.signal()
        }
        
        stateLock.wait()
        
        while (state != .stopped) {
            //log.info("Monitor thread state is: \(state)")
            stateLock.signal()
            
            if state != .stopped && state != .paused {
                stateLock.wait()
                
                do {
                    defer {
                        stateLock.signal()
                    }
                    
                    for (_, listener) in listeners {
                        DispatchQueue.main.async {
                            listener.onRefresh()
                        }
                    }
                }
            }
            
            if (state == .stopped) {
                return
            }
            
            Thread.sleep(forTimeInterval: refreshInterval)
        }
    }
}

extension MonitorService : TypeNameDescribable {}
