//
//  UriUploader.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/24.
//

import Foundation

import Logging

struct UriUploader {
    static func doUpload(uri: Uri?, pairHelper: PairHelper) -> Bool {
        let log = Logger(label: UriUploader.typeName)
        
        guard let uri = uri else {
            return false
        }
        
        do {
            if try !pairHelper.getAllUrls().keys.contains(uri.toString()) {
                pairHelper.uploadUri(uri)
                return true
            }
        } catch {
            log.warning("Error getting uri string: \(error)")
        }

        return false
    }
}

extension UriUploader : TypeNameDescribable {}
