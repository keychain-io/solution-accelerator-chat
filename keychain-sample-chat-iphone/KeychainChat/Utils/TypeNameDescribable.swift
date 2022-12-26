//
//  TypeNameDescribable.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/07.
//

import Foundation

protocol TypeNameDescribable {
    var typeName: String { get }
    static var typeName: String { get }
}

extension TypeNameDescribable {
    var typeName: String {
        return String(describing: type(of: self))
    }

    static var typeName: String {
        return String(describing: self)
    }
}
