//
//  LogonListener.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2022/02/06.
//

import Foundation

protocol LoginListener {
    func onLogin()
    
    func onLogoff()
}
