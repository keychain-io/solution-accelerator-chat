//
//  ContactListView.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 8/29/21.
//

import SwiftUI

import Logging
import CodeScanner

struct ContactListView: View {
    
    let log = Logger(label: ContactListView.typeName)
    
    @EnvironmentObject var chatViewModel: ChatViewModel
    @EnvironmentObject var contactViewModel: ContactViewModel
    @EnvironmentObject var personaViewModel: PersonaViewModel
    @EnvironmentObject var authentication: Authentication
    
    @State var isSourceMenuShowing = false
    @State var filterText = ""
    
    @State var showingEditContact = false
    @State var name = ""
    @State var subName = ""
    @State var selectedContact = Contact()

    @Binding var isShowingSendToQRCode: Bool
    @Binding var isShowingMyQRCode: Bool
    @Binding var isChatShowing: Bool
    @Binding var isSendToAllContacts: Bool
    @Binding var chatRecipient: User
    @Binding var participants: [User]

    var keychainService = KeychainService.instance
    
    let labelWidth: CGFloat = 80
    let labelHeight: CGFloat = 12
        
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HeaderView(title: "Contacts")
            
            Divider()
                .frame(height: 1)
                .background(Color("text-primary"))
                .padding(.top)
            
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    // Search bar
//                    ZStack {
//                        Rectangle()
//                            .foregroundColor(.white)
//                            .cornerRadius(20)
//                        
//                        TextField("Search for contact", text: $filterText)
//                            .font(.tabBar)
//                            .padding()
//                    }
//                    .frame(height: 46)
                    
                    Spacer()
                                        
                    Button(action: {
                        isSourceMenuShowing = true
                    }) {
                        Image(systemName: "qrcode")
                            .foregroundColor(Color("button-primary"))
                            .font(.system(size: 25))
                    }
                    .sheet(isPresented: $isShowingSendToQRCode) {
                        getQRCodeScanner()
                    }
                    .sheet(isPresented: $isShowingMyQRCode) {
                        // If there is no active persona, we would not be here
                        getPersonaDetailsView()
                    }
                }
                .padding(.vertical)
                
                if contactViewModel.filteredUsers.count > 0 {
                    List {
                        ForEach(contactViewModel.filteredUsers) { user in
                            
                            Button {
                                chatRecipient = user
                                chatViewModel.getChatFor(
                                    senderUri: personaViewModel.getUriString(chatViewModel.getActivePersona()!),
                                    contact: user)
                                
                                isSendToAllContacts = false
                                isChatShowing = true
                            } label: {
                                ContactRow(user: user)
                            }
                            .buttonStyle(.plain)
                            .swipeActions(edge: .leading) {
                                Button() {
                                    guard let contact = contactViewModel.getContact(uri: user.uri) else {
                                        
                                        log.error("Error getting contact for: \(user.getName())")
                                        return
                                    }
                                    
                                    name = user.firstName ?? ""
                                    subName = user.lastName ?? ""
                                    selectedContact = contact
                                    showingEditContact = true
                                } label: {
                                    Label("Edit", systemImage: "highlighter")
                                }
                                .tint(.blue)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    contactViewModel.deleteContact(user)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                            .sheet(isPresented: $showingEditContact) {
                                ContactEditView(isPresented: $showingEditContact,
                                                name: name,
                                                subName: subName,
                                                selectedContact: $selectedContact)
                            }
                        }
                        
                        Spacer()
                    }
                    .listRowBackground(Color.white)
                    .listStyle(.plain)
                } else {
                    Spacer()
                    
                    HStack {
                        Spacer()
                        
                        VStack(alignment: .center) {
                            Image("no-contacts-yet")
                            
                            Text("Hmm... Zero contacts?")
                                .font(.titleText)
                                .multilineTextAlignment(.center)
                                .padding(.top, 32)
                            
                            Text("Try pairing some contacts using QR code or trusted directory!")
                                .font(.bodyParagraph)
                                .multilineTextAlignment(.center)
                                .padding(.top, 8)
                        }
                        
                        Spacer()
                    }
                    
                    Spacer()
                }
            }
        }
        .confirmationDialog("QR Code or Scanner or Trusted Directory", isPresented: $isSourceMenuShowing, actions: {
            Button {
                // Show my QR code
                isShowingMyQRCode = true
            } label: {
                Text("My QR Code")
            }

            Button {
                // Show QR code scanner
                    isShowingSendToQRCode = true
            } label: {
                Text("QR Code Scanner")
            }
            
            Button {
                chatViewModel.pairUsingTrustedDirectory()
            } label: {
                Text("From Trusted Directory")
            }
        })
        .fullScreenCover(isPresented: $isChatShowing, onDismiss: nil) {
            ConversationView(isChatShowing: $isChatShowing,
                             isSendToAllContacts: $isSendToAllContacts,
                             chatRecipient: $chatRecipient,
                             participants: $participants)
        }
        .onAppear() {
            contactViewModel.onViewAppeared()
        }
        .padding(.horizontal)
        .accentColor(.blue)
    }
    
    func setBackground() {
        UITableViewCell.appearance().backgroundColor = .white
        UITableView.appearance().backgroundColor = .white
        UITabBar.appearance().backgroundColor = .gray
    }

    func getQRCodeView() -> QRCodeView {
        setBackground()
        
        var qrCodeData = ""
        
        if let activePersona = keychainService?.activePersona {
            qrCodeData = personaViewModel.getQRCodeData(persona: activePersona)
        }
        
        return contactViewModel.getQRCodeView(qrCodeData)
        
    }
    
    func getPersonaDetailsView() -> PersonaDetailView? {
        // We definitely have a persona if we are at this point
        guard let persona = keychainService?.activePersona! else { return nil };
        
        return PersonaDetailView(persona: persona,
                          user: personaViewModel.getUser(persona: persona))
    }
    
    func getActivePersonaLeftName() -> Text {
        if let activePersona = keychainService?.activePersona {
            let name = personaViewModel.getLeftName(activePersona)
            return Text(name)
        }
        
        return Text("")
    }
    
    func getActivePersonaRightName() -> Text {
        if let activePersona = keychainService?.activePersona {
            let name = personaViewModel.getRightName(activePersona)
            return Text(name)
        }
        
        return Text("")
    }
    
    func getActivePersonaUri() -> Text {
        if let activePersona = keychainService?.activePersona {
            return Text(personaViewModel.getUriString(activePersona))
        }
        
        return Text("")
    }
    
    func getQRCodeScanner() -> CodeScannerView {
        return CodeScannerView(codeTypes: [.qr],
                               simulatedData: "Simulated Data",
                               completion: self.handleScan)
        
    }
    
    func handleScan(result: Result<String, CodeScannerView.ScanError>) {
        self.isShowingSendToQRCode = false
        
        switch result {
            case .success(let rawResult):
                let result = contactViewModel.processQRCodeScanResult(rawResult)
                
                if result.status == .new {
                    guard let qrCodeData = result.qrCodeData else {
                        log.error("No QR code data")
                        return
                    }
                    
                    DispatchQueue.global(qos: .userInteractive).async {
                        chatViewModel.pair(uri: qrCodeData.id)
                    }
                }
            case .failure(let error):
                log.error("Scanning failed: \(error)")
        }
    }
    
    func getChatName(_ user: User) -> String {
        let firstName = user.firstName ?? ""
        let lastName = user.lastName ?? ""
        
        return "\(firstName) \(lastName)"
    }
}

struct ContactListView_Previews: PreviewProvider {
    @State static var tabIndex = 1
    
    static var previews: some View {
        ContactListView(isShowingSendToQRCode: .constant(false),
                        isShowingMyQRCode: .constant(false),
                        isChatShowing: .constant(false),
                        isSendToAllContacts: .constant(false),
                        chatRecipient: .constant(getUser()),
                        participants: .constant([User]()))
            .environmentObject(Authentication())
            .environmentObject(ContactViewModel())
            .environmentObject(PersonaViewModel())
    }
    
    static func getUser() -> User {
        let user = User()
        user.firstName = "FirstName"
        user.lastName = "LastName"
        
        return user
    }
}

extension ContactListView : TypeNameDescribable {}

