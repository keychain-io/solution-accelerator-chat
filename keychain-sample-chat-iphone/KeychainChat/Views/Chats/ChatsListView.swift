//
//  ChatsListView.swift
//  swiftui-chat
//
//  Created by Robert Ellis on 2022/06/01.
//

import SwiftUI

import Logging

struct ChatsListView: View {
    
    @EnvironmentObject var chatViewModel: ChatViewModel
    @EnvironmentObject var contactViewModel: ContactViewModel
    @EnvironmentObject var personaViewModel: PersonaViewModel
    
    @Binding var isChatShowing: Bool
    @Binding var isSendToAllContacts: Bool
    @Binding var chatRecipient: User

    var keychainService = KeychainService.instance

    var body: some View {
        
        VStack {
            
            // Heading
            HeaderView(title: "Chats")

            Divider()
                .frame(height: 1)
                .background(Color.blue)

            // Chat list
            if chatViewModel.chats.count > 0 {
                
                List(chatViewModel.chats) { chat in
                    
                    Button {
                        
                        // Set selcted chat for the chatviewmodel
                        chatViewModel.selectedChat = chat
                        chatRecipient = getChatRecipient(chat)

                        if let uri = chat.participantIds?.last {
                            isSendToAllContacts = uri == Constants.all
                        } else {
                            isSendToAllContacts = false
                        }
                        
                        // display conversation view
                        isChatShowing = true
                        
                    } label: {
                        ChatsListRow(chat: chat,
                                     otherParticipants: contactViewModel.getParticipants(ids: chat.participantIds ?? [
                                     String]()))
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
            }
            else {
                
                Spacer()
                
                Image("no-chats-yet")
                
                Text("Hmm... no chats here yet!")
                    .font(Font.titleText)
                    .padding(.top, 32)
                
                Text("Chat a friend to get started")
                    .font(Font.bodyParagraph)
                    .padding(.top, 8)
                
                Spacer()
            }
        }
    }
    
    private func getChatRecipient(_ chat: Chat) -> User {
        guard let uri = chat.participantIds?.last else {
            return User()
        }
        
        var user = User()
        
        keychainService?.chatRepository.getPlatformUser(uri: uri, completion: { platformUser in

            user = platformUser ?? User()
        })
        
        
        return user
    }
}

struct ChatsListView_Previews: PreviewProvider {
    static var previews: some View {
        ChatsListView(isChatShowing: .constant(false), isSendToAllContacts: .constant(false), chatRecipient: .constant(User()))
    }
}
