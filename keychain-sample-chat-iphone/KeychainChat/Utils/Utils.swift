//
//  Utils.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 10/4/21.
//

import Foundation

import OrderedCollections

struct Utils {
    
    static func sortUsers(users: inout [User], usersDict: [String: User]) {
        var byNameDict = OrderedDictionary<String, User>()
        
        for user in usersDict.values {
            let name = "\(user.firstName ?? "") \(user.lastName ?? "")"
            byNameDict[name] = user
        }
        
        users = byNameDict.values.map({$0})
    }
    
    func getDocumentsDirectory() -> URL {
        // find all possible documents directories for this user
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        
        // just send back the first one, which ought to be the only one
        return paths[0]
    }
    
    static func dateToString(_ date: Date) -> String {
        // Create Date Formatter
        let dateFormatter = DateFormatter()

        // Set Date/Time Style
        dateFormatter.dateStyle = .long
        dateFormatter.timeStyle = .long

        // Convert Date to String
        return dateFormatter.string(from: date)
    }
    
    static func dateFromString(_ date: String) -> Date? {
        // Create Date Formatter
        let dateFormatter = DateFormatter()

        // Set Date/Time Style
        dateFormatter.dateStyle = .long
        dateFormatter.timeStyle = .long

        // Convert Date to String
        return dateFormatter.date(from: date) ?? nil
    }
}

extension Date {
    var millisecondsSince1970: Int64 {
        Int64((self.timeIntervalSince1970 * 1000.0).rounded())
    }
    
    init(milliseconds: Int64?) {
        self = Date(timeIntervalSince1970: TimeInterval(milliseconds ?? 0) / 1000)
    }
}

extension Encodable {
    var convertToJsonString: String? {
        let jsonEncoder = JSONEncoder()
        
        jsonEncoder.outputFormatting = .prettyPrinted
        
        do {
            let jsonData = try jsonEncoder.encode(self)
            return String(data: jsonData, encoding: .utf8)
        } catch {
            return nil
        }
    }
}

extension String {
    //Base64 decode
    func fromBase64() -> String? {
        guard let data = Data(base64Encoded: self) else {
            return nil
        }
        
        return String(data: data, encoding: .utf8)
    }
    
    //Base64 encode
    func toBase64() -> String {
        return Data(self.utf8).base64EncodedString()
    }
}
