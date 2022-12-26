//
//  AppConfig.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/08.
//

import Foundation
import UIKit
import SwiftUI

import Logging

public struct AppConfig {
    static func get() -> Config {
        let log = Logger(label: AppConfig.typeName)
        
        log.info("Loading Config.plist")
        
        guard let url = Bundle.main.url(forResource: "Config", withExtension: "plist") else {
            fatalError("Could not find Config.plist in your Bundle")
        }

        log.info("Loading \(url)")
        
        do {
            let data = try Data(contentsOf: url)
            let decoder = PropertyListDecoder()
            
            log.info("Decoding data:")
            log.info("\(data)")
            
            return try decoder.decode(Config.self, from: data)
        } catch {
            fatalError("\(Constants.somethingWentWrong): \(error)")
        }
    }
}

extension AppConfig : TypeNameDescribable {}
