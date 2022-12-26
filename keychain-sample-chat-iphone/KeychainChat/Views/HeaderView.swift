//
//  HeaderView.swift
//  consumer-app-iphone
//
//  Created by Robert Ellis on 2021/12/23.
//

import SwiftUI

struct HeaderView: View {
    @EnvironmentObject var keychainViewModel: ChatViewModel
    @EnvironmentObject var personaViewModel: PersonaViewModel
    @EnvironmentObject var contactViewModel: ContactViewModel
    @EnvironmentObject var authentication: Authentication

    var keychainService = KeychainService.instance
    
    var title: String
    
    init(title: String) {
        self.title = title
    }
    
    var body: some View {
        VStack {
            HStack {
                VStack(alignment: .leading) {
                    Text(title)
                        .foregroundColor(Color("text-primary"))
                        .font(.pageTitle)
                        .bold()
                    Text(getName())
                        .foregroundColor(Color("text-primary"))
                }
                
                Spacer()
                
                getMqttConnectionState()
                    .foregroundColor(keychainViewModel.isMqttConnected() ? Color("mqtt-connected") : Color("mqtt-disconnected"))
                    .font(.system(size: 30))
                    .padding(.horizontal)
                
                Button {
                    authentication.updateValidation(false)
                    contactViewModel.reset()
                } label: {
                    Image(systemName: Constants.logoutImageName)
                        .foregroundColor(Color("button-primary"))
                        .font(.system(size: 30))
                }
            }
        }
        .padding([.top, .leading, .trailing])
    }
    
    func getMqttConnectionState() -> Image {
        let imageName = keychainViewModel.isMqttConnected() ? "wifi" : "wifi.exclamationmark"
        
        return Image(systemName: imageName)
    }

    func getName() -> String {
        if let persona = keychainService?.activePersona {
            return "\(personaViewModel.getLeftName(persona)) \(personaViewModel.getRightName(persona))"
        }
        
        return ""
    }
}
