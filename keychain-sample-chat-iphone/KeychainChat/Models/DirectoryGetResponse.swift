//
//  DirectoryGetResponse.swift
//  
//
//  Created by Robert Ellis on 2021/12/07.
//

import Foundation

public struct DirectoryGetResponse: Codable {
    
    public var response_code: String
    
    public var results: [DirectoryEntry]
}
