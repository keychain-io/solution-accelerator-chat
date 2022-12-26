//
//  TextHelper.swift
//  swiftui-chat
//
//  Created by Robert Ellis on 2022/06/01.
//

import Foundation

class StringUtils {
    static public func isXML(text: String) -> Bool {
        return text.starts(with: "<?xml")
    }
    
    static public func arrayContains(array: [String]?, element: String) -> Bool {
        guard let array = array else {
            return false
        }
        
        return array.map({$0}).contains(element)
    }

    static public func arrayContains(text: String, delimiter: String, element: String) -> Bool {
        let arr = text.components(separatedBy: delimiter)
        return arrayContains(array: arr, element: element)
    }
}

extension StringProtocol {
    var utf8Data: Data { .init(utf8) }
    var utf8Bytes: [UInt8] { .init(utf8) }
}
