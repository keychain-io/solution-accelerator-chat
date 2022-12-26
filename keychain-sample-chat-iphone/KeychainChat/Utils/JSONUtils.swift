//
//  JSONUtils.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 9/26/21.
//

import Foundation

import Logging

struct JSONUtils {

    static let log = Logger(label: JSONUtils.typeName)

    static func toJson<T: Encodable>(target: T) -> String? {
        var data: Data!
        
        do {
            let encoder = JSONEncoder()
            data = try encoder.encode(target)
            let json = String(data: data, encoding: .utf8)
            return json
        } catch {
            log.error("\(error)")
        }
        
        return nil
    }
    
    static func fromJson<T: Decodable>(_ dump: T.Type, json: String) -> T? {
        do {
            let data = Data(json.utf8)
            
            // Decode the data with a JSON decoder
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            log.error("\(error)")
        }

        return nil
    }
}

extension JSONUtils : TypeNameDescribable {}
