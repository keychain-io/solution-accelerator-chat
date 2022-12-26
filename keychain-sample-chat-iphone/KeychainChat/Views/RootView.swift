//
//  RootView.swift
//  swiftui-chat
//
//  Created by Robert Ellis on 2022/06/01.
//

import SwiftUI

struct RootView: View {
    
    @State var selectedTab: Tabs = .contacts
    @State var chatRecipient = User()
    @State var participants = [User]()

    @State var isShowingSendToQRCode = false
    @State var isShowingMyQRCode = false
    @State var isChatShowing = false
    @State var isSendToAllContacts = false

    var body: some View {
        
        ZStack {
            
            Color("background")
                .ignoresSafeArea()
            
            VStack {
                
                switch selectedTab {
                case .chats:
                        ChatsListView(isChatShowing: $isChatShowing,
                                      isSendToAllContacts: $isSendToAllContacts,
                                      chatRecipient: $chatRecipient)
                case .contacts:
                    ContactListView(isShowingSendToQRCode: $isShowingSendToQRCode,
                                    isShowingMyQRCode: $isShowingMyQRCode,
                                    isChatShowing: $isChatShowing,
                                    isSendToAllContacts: $isSendToAllContacts,
                                    chatRecipient: $chatRecipient,
                                    participants: $participants)
                }
                
                Spacer()
                
                CustomTabBar(selectedTab: $selectedTab,
                             chatRecipient: $chatRecipient,
                             isChatShowing: $isChatShowing,
                             isSendToAllContacts: $isSendToAllContacts)
            }
        }
        .fullScreenCover(isPresented: $isChatShowing, onDismiss: nil) {
            
            // The conversation view
            ConversationView(isChatShowing: $isChatShowing,
                             isSendToAllContacts: $isSendToAllContacts,
                             chatRecipient: $chatRecipient,
                             participants: $participants)
        }
        
    }
    
}

struct RootView_Previews: PreviewProvider {
    static var previews: some View {
        RootView()
    }
}
