//
//  ConversationView.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/05/24.
//

import SwiftUI

import Logging

struct ConversationView: View {
    let log = Logger(label: ConversationView.typeName)
    
    @State private var messageText = ""
    
    @EnvironmentObject var contactViewModel: ContactViewModel
    @EnvironmentObject var chatViewModel: ChatViewModel
    @EnvironmentObject var personaViewModel: PersonaViewModel
    
    @Binding var isChatShowing: Bool
    @Binding var isSendToAllContacts: Bool
    @Binding var chatRecipient: User
    @Binding var participants: [User]

    @State var selectedImage: UIImage?
    @State var isPickerShowing = false
    
    @State var isSourceMenuShowing = false
    @State var source: UIImagePickerController.SourceType = .photoLibrary

    @State var chatMessage = ""

    var keychainService = KeychainService.instance

    var body: some View {
        VStack {
            // Chat header
            HStack {
                VStack (alignment: .leading) {
                    // Back arrow
                    Button {
                        // Dismiss chat window
                        isChatShowing = false
                    } label: {
                        Image(systemName: "arrow.backward")
                            .resizable()
                            .frame(width: 24, height: 24)
                            .scaledToFit()
                            .foregroundColor(.black)
                    }
                    .padding(.bottom, 16)
                    
                    // Name
                    Text(!isSendToAllContacts ? chatRecipient.getName() : "Send to ALL")
                        .font(.title)
                }
                
                Spacer()
                
                getProfilePicView()
            }
            .padding(.horizontal)
            .frame(height: 104)

            Divider()
                .frame(height: 1)
                .background(Color("line-color"))

            // Chat log
            ScrollViewReader { proxy in
            
                ScrollView {
                    
                    VStack (spacing: 24) {
                        
                        ForEach (Array(chatViewModel.messages.enumerated()), id: \.element) { index, msg in
                            
                            let isFromMe = msg.senderId == getMyUri()
                            
                            // Dynamic message
                            HStack {
                                
                                if isFromMe {
                                    // Timestamp
                                    Text(DateHelper.chatTimestampFrom(date: Date(milliseconds: msg.timestamp)))
                                        .font(Font.bodyParagraph)
                                        .foregroundColor(Color("text-timestamp"))
                                        .padding(.trailing)
                                    
                                    Spacer()
                                }
                                
                                if msg.imageUrl != "" && msg.imageUrl != nil {
                                    // Photo Message
                                    ConversationPhotoMessage(imageUrl: msg.imageUrl!,
                                                             isFromMe: isFromMe,
                                                             isSendToAllContacts: isSendToAllContacts,
                                                             name: chatRecipient.getName())
                                }
                                else {
                                    getConversationTextMessage(message: msg, isFromMe: isFromMe)
                                        .foregroundColor(Color.black)
                                }
                                
                                if !isFromMe {
                                    Spacer()
                                    
                                        Text(DateHelper.chatTimestampFrom(date: Date(milliseconds: msg.timestamp)))
                                        .font(Font.bodyParagraph)
                                        .foregroundColor(Color("text-timestamp"))
                                        .padding(.leading)
                                }
                            }
                            .id(index)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 24)
                }
                .background(Color("background"))
                .onChange(of: chatViewModel.messages.count) { newCount in
                    
                    withAnimation {
                        proxy.scrollTo(newCount - 1)
                    }
                }
            }
            
            // Chat message bar
            ZStack {
                Color("background")
                    .ignoresSafeArea()
                
                HStack (spacing: 15) {
                    // Camera button
                    Button {
                        // Show picker
                        isSourceMenuShowing = true
                        
                    } label: {
                        Image(systemName: "camera")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 24, height: 24)
                            .tint(Color("icons-secondary"))
                    }

                    // Textfield
                    ZStack {
                        
                        Rectangle()
                            .foregroundColor(Color("date-pill"))
                            .cornerRadius(50)
                        
                        if selectedImage != nil {
                            
                            // Display image in message bar
                            Text("Image")
                                .foregroundColor(Color("text-input"))
                                .font(Font.bodyParagraph)
                                .padding(10)
                            
                            // Delete button
                            HStack {
                                Spacer()
                                
                                Button {
                                    // Delete the image
                                    selectedImage = nil
                                    
                                } label: {
                                    Image(systemName: "multiply.circle.fill")
                                        .resizable()
                                        .scaledToFit()
                                        .frame(width: 24, height: 24)
                                        .foregroundColor(Color("text-input"))
                                }
                            }
                            .padding(.trailing, 12)
                        }
                        else {
                        
                            TextField("Type your message", text: $chatMessage)
                                .padding(10)
                        }
                    }
                    .frame(height: 44)
                    
                    // Send button
                    Button {
                        
                        // Check if image is selected, if so send image
                        if selectedImage != nil {
                            
                            // Send image message
                            chatViewModel.sendPhotoMessage(senderUri: getMyUri(), recipientUri: chatRecipient.uri!, image: selectedImage!)
                            
                            // Clear image
                            selectedImage = nil
                        }
                        else {
                            // Send text message
                            
                            // Clean up text msg
                            chatMessage = chatMessage.trimmingCharacters(in: .whitespacesAndNewlines)
                            
                            if isSendToAllContacts {
                                var chat = Chat()
                                chat.participantIds = [getMyUri(), Constants.all]
                                
                                chatViewModel.selectedChat?.participantIds = [getMyUri(), Constants.all]
                                
                                chatViewModel.sendMessage(sender: getMyUri(), receiver: Constants.all, msg: chatMessage)
                            } else {
                                chatViewModel.sendMessage(sender: getMyUri(), receiver: chatRecipient.uri!, msg: chatMessage)
                            }

                            // Clear textbox
                            chatMessage = ""
                        }
                    } label: {
                        Image(systemName: "paperplane.fill")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 24, height: 24)
                            .tint(Color("icons-primary"))
                    }
                    .disabled(chatMessage.trimmingCharacters(in: .whitespacesAndNewlines) == "" &&
                    selectedImage == nil)
                }
                .padding(.horizontal)
            }
            .frame(height: 76)
        }
        .onAppear {
            do {
                // Call chat view model to retrieve all chat messages
                chatViewModel.getMessages()
                
                if let activePersonaUri = try chatViewModel.getActivePersona()?.getUri().toString() {
                    // Try to get the other participants as User instances
                    let ids = chatViewModel.selectedChat?.participantIds?.filter({ id in
                        id != activePersonaUri
                    })
                        
                    self.participants = contactViewModel.getParticipants(ids: ids ?? [String]())
                }
            } catch {
                log.error("\(Constants.somethingWentWrong): \(error)")
            }
        }
        .confirmationDialog("From where?", isPresented: $isSourceMenuShowing, actions: {
            
            Button {
                // Set the source to photo library
                self.source = .photoLibrary
                
                // Show the image picker
                isPickerShowing = true
                
            } label: {
                Text("Photo Library")
            }

            if UIImagePickerController.isSourceTypeAvailable(.camera) {
            
                Button {
                    // Set the source to camera
                    self.source = .camera
                    
                    // Show the image picker
                    isPickerShowing = true
                } label: {
                    Text("Take Photo")
                }
            }
        })
        .sheet(isPresented: $isPickerShowing) {
            
            // Show the image picker
            ImagePicker(selectedImage: $selectedImage,
                        isPickerShowing: $isPickerShowing, source: self.source)
        }
    }
    
    private func getProfilePicView() -> ProfilePicView {
        let participants = getParticipants()
        
        // Profile image
        if participants.count > 0 {
            
            let participant = participants.first
            
            return ProfilePicView(user: participant!, backgroundColor: Color("circle-contact"), textColor: Color("circle-contact-text"))
        }
        
        return ProfilePicView(user: chatRecipient, backgroundColor: Color("circle-contact"), textColor: Color("circle-contact-text"))
    }
    
    private func isFromMe(_ msg: ChatMessage) -> Bool {
        do {
            return try msg.senderId == keychainService?.getActivePersona()?.getUri().toString()
        } catch {
            log.error("Error getting active persona URI: \(error)")
            return false
        }
    }
    
    func getMyUri() -> String {
        do {
            guard let uri = try chatViewModel.getActivePersona()?.getUri().toString() else {
                log.error("\(Constants.somethingWentWrong): No active persona.")
                return ""
            }
            
            return uri
        } catch {
            log.error("\(Constants.somethingWentWrong): \(error)")
            return ""
        }
    }
    
    func getParticipants() -> [User] {
        var participants = [User]()
        
        guard let ids = chatViewModel.selectedChat?.participantIds else {
            return participants
        }
        
        for uri in ids {
            if uri != Constants.all {
                guard let user = personaViewModel.usersDict[uri] else {
                    continue
                }
                
                participants.append(user)
            } else {
                keychainService?.chatRepository.getPlatformUser(firstName: Constants.all, lastName: "", completion: { [self] user in
                    guard let user = user else {
                        log.warning("No user for ALL chats")
                        return
                    }
                    
                    participants.append(user)
                })
            }
            
        }
        
        return participants
    }
    
    fileprivate func getConversationTextMessage(message: ChatMessage, isFromMe: Bool) -> ConversationTextMessage {
        var senderName = ""
        
        if let senderUri = message.senderId {
            if message.sendOrRcvd == .receive {
                if let contact = keychainService?.findContact(senderUri) {
                    senderName = contactViewModel.getName(contact)
                }
            }
        }

        let decryptedMessage: String = chatViewModel.getDecryptedMessage(message.msg ?? "")
        let conversationText = ConversationTextMessage(
            msg: decryptedMessage,
            name: senderName,
            isFromMe: isFromMe,
            isSendToAllContacts: isSendToAllContacts)
        
        return conversationText
    }
    
}

extension ConversationView : TypeNameDescribable {}

//struct ConversationView_Previews: PreviewProvider {
//    static var previews: some View {
//        ConversationView(userName: "Lucky Larry", group: "My Group", isChatShowing: .constant(true))
//    }
//}
