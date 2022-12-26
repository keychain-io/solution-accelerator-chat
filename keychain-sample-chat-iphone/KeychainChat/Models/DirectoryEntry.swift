//
//  DirectoryEntry.swift
//  
//
//  Created by Robert Ellis on 2021/12/07.
//

import Foundation

public struct DirectoryEntry: Codable {
    public let name: String
    
    public let encr_txid: String

    public let encr_vout: Int

    public let sign_txid: String

    public let sign_vout: Int
}
