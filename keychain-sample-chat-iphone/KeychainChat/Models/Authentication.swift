//
//  Authentication.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/11/22.
//

import SwiftUI

class Authentication: ObservableObject {
    @Published var isValidated = false
    
    var loginListener: LoginListener?
    
    func updateValidation(_ success: Bool) {
        if success  {
            if loginListener != nil {
                loginListener?.onLogin()
            }
        } else {
            if loginListener != nil {
                loginListener?.onLogoff()
            }
        }
        
        withAnimation {
            isValidated = success
        }
    }
}
