//
//  KeychainChatApp.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/05/24.
//

import SwiftUI

@main
struct KeychainChatApp: SwiftUI.App {
    @StateObject var authentication = Authentication()
    @StateObject var loginViewModel = AuthViewModel()
    @StateObject var pesonaViewModel = PersonaViewModel()
    @StateObject var contactViewModel = ContactViewModel()
    @StateObject var chatViewModel = ChatViewModel()

    @StateObject var mqttService = MqttService.shared()
    
    init() {
        UITableViewCell.appearance().backgroundColor = .white
        UITableView.appearance().backgroundColor = .white
        
        UITabBar.appearance().backgroundColor = .white
    }

    var body: some Scene {
        WindowGroup {
            if authentication.isValidated {
                RootView()
                    .environmentObject(authentication)
                    .environmentObject(loginViewModel)
                    .environmentObject(pesonaViewModel)
                    .environmentObject(contactViewModel)
                    .environmentObject(chatViewModel)
                    .environmentObject(mqttService)
            } else {
                PersonaListView(isProgressShowing: $pesonaViewModel.progressShowing)
                    .environmentObject(authentication)
                    .environmentObject(loginViewModel)
                    .environmentObject(pesonaViewModel)
                    .environmentObject(contactViewModel)
                    .environmentObject(chatViewModel)
            }

        }
    }
}
