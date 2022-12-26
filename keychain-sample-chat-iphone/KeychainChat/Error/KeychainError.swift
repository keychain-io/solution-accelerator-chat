//
//  KeychainError.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/20.
//

import Foundation

public enum KeychainError: Error {
    case unexpected
    case nullPointer
    case gatewayError(String)
    case runtimeError(String)
}

extension KeychainError: LocalizedError {
    public var errorDescription: String? {
        switch self {
            case .unexpected:
                return NSLocalizedString(
                    "An unexpected error occurred.",
                    comment: "Unexpected Error"
                )
            case .nullPointer:
                return NSLocalizedString(
                    "Null pointer exception occurred.",
                    comment: "Null pointer detected"
                )
            case .gatewayError(_):
                return self.failureReason
            case .runtimeError(_):
                return self.failureReason
        }
    }
}
