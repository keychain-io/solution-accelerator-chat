//
//  LoginViewModel.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/11/19.
//

import Foundation
import SwiftUI

import Logging

class AuthViewModel: ObservableObject {
    @Published var persona: Persona?
    @Published var loginStatus = PersonaLoginStatus.none
    @Published var showProgressView = false
    
    let log = Logger(label: AuthViewModel.typeName)
    
    private var keychainService = KeychainService.instance
    
    var loginEnabled: Bool {
        do {
            if let p = persona {
                return try PersonaStatus(rawValue: p.getStatus().intValue) == .confirmed
            }
        } catch {
            log.error("Error getting persona status: \(error)")
        }
        
        return false
    }
    
    func setActivePersona(completion: @escaping (Bool) -> Void) {
        showProgressView = true
        
        defer {
            showProgressView = false
        }
        
        do {
            if let persona = self.persona {
                if try persona.isMature().boolValue {
                    guard let keychainService = keychainService else {
                        throw KeychainError.runtimeError("Gateway service not initialized")
                    }
                    
                    loginStatus = keychainService.setActivePersona(persona) ? .ok : .failure
                    
                    switch loginStatus {
                        case .ok:
                            log.info("Login successful.")
                            completion(true)
                            
                        default:
                            log.error("Error setting active persona.")
                            self.persona = try Persona(0)
                            completion(false)
                    }
                } else {
                    completion(false)
                }
            }
        } catch {
            log.error("Error setting active persona: \(error)")
            completion(false)
        }
    }
    
    static func getLoggedInUserId() -> String {
        do {
            let keychainService = KeychainService.instance
            
            guard let uri = try keychainService?.getActivePersona()?.getUri().toString() else {
                return ""
            }
            
            return uri
        } catch {
            let log = Logger(label: AuthViewModel.typeName)
            log.error("\(Constants.somethingWentWrong): \(error)")
            
            return ""
        }
    }
}

extension AuthViewModel : TypeNameDescribable {}
