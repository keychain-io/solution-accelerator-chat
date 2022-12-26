//
//  User.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/06/03.
//

import Foundation

class User: Codable, Identifiable {
    var id: String = UUID().uuidString
    
    var firstName: String?
    
    var lastName: String?
    
    var status: Int?
    
    var photo: String?
    
    var uri: String?
    
    public func getName() -> String {
        guard let fName = firstName,
              let lName = lastName else {
            return ""
        }
        
        return "\(fName) \(lName)"
    }
    
    public func getInitials() -> String {
        return "\(firstName?.prefix(1) ?? "")\(lastName?.prefix(1) ?? "")"
    }
    
    public func getKey() -> String {
        if status != PersonaStatus.confirmed.rawValue {
            return getName()
        }
        
        guard let uri = uri else {
            // If there is not uri yet, use the full name as the key
            return getName()
        }
        
        guard uri.count > 100 else {
            return getName()
        }

        return uri
    }
}
