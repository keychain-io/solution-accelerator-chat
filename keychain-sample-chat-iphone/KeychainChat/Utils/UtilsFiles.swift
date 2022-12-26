//
//  UtilsFiles.swift
//  
//
//  Created by Robert Ellis on 2021/12/10.
//

import Foundation
import SwiftUI

public struct UtilsFiles {
    
    static func getDocumentsDirectory() -> URL {
        // find all possible documents directories for this user
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)

        // just send back the first one, which ought to be the only one
        return paths[0]
    }

    static func exists(name: String, ofType: StringLiteralType) -> Bool {
        let fileManager = FileManager.default
        
        let path = getDocumentsDirectory().appendingPathComponent("\(name).\(ofType)").path
        
        return fileManager.fileExists(atPath: path)
    }
        
    static func getPath(fileName: String, extention: String) throws -> String {
        guard let path = Bundle.main.path(forResource: fileName, ofType: extention) else {
            throw KeychainError.runtimeError("\(fileName).\(extention) not found")
        }
        
        return path
    }
    
    static func createDirectory(path: URL) throws {
        try FileManager.default.createDirectory(at: path, withIntermediateDirectories: true, attributes: nil)
    }
}
