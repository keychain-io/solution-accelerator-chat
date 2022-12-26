//
//  CustomTabBar.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/05/30.
//

import SwiftUI

enum Tabs: Int {
    case chats = 0
    case contacts = 1
}

struct CustomTabBar: View {
    @EnvironmentObject var chatViewModel: ChatViewModel
    @EnvironmentObject var personaViewModel: PersonaViewModel

    @Binding var selectedTab: Tabs
    @Binding var chatRecipient: User
    @Binding var isChatShowing: Bool
    @Binding var isSendToAllContacts: Bool

    var body: some View {
        HStack {
            Button {
                selectedTab = .chats
            } label: {
                TabBarButton(buttonText: "Chats",
                             imageName: "bubble.left",
                             isActive: selectedTab == .chats)
            }
            .tint(Color("icons-secondary"))
            
            Button {
                chatRecipient = User()
                chatRecipient.firstName = Constants.all
                chatRecipient.uri = Constants.all
                chatRecipient.status = PersonaStatus.confirmed.rawValue
                
                chatViewModel.getChatFor(
                    senderUri: personaViewModel.getUriString( chatViewModel.getActivePersona()!),
                    contact: chatRecipient)
                
                isSendToAllContacts = true
                isChatShowing = true
            } label: {
                GeometryReader { geo in
                    VStack (alignment: .center, spacing: 4) {
                        Image(systemName: "person.3.fill")
                            .resizable()
                            .scaledToFit()
                            .foregroundColor(Color("button-primary"))
                            .frame(width: 32, height: 32)
                        
                        Text("Chat with ALL")
                            .foregroundColor(Color("text-primary"))
                            .font(.tabBar)
                    }
                    .frame(width: geo.size.width, height: geo.size.height)
                }
            }
            .tint(Color("icons-primary"))

            Button {
                selectedTab = .contacts
            } label: {
                TabBarButton(buttonText: "Contacts",
                             imageName: "person",
                             isActive: selectedTab == .contacts)
            }
            .tint(Color("icons-secondary"))

        }
        .frame(height: 82)
    }
}

struct CustomTabBar_Previews: PreviewProvider {
    static var previews: some View {
        CustomTabBar(selectedTab: .constant(.contacts),
                     chatRecipient: .constant(User()),
                     isChatShowing: .constant(false),
                     isSendToAllContacts: .constant(false))
    }
}
