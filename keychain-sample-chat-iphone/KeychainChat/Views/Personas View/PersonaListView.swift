//
//  ConversationView.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 8/16/21.
//

import SwiftUI
import os

import Logging

struct PersonaListView: View {
    let log = Logger(label: PersonaListView.typeName)
    
    @Binding var isProgressShowing: Bool
    @State var showingAddPersona = false
    @State var showingEditPersona = false
    @State var name = ""
    @State var subName = ""
    @State var selectedPersona = Persona()

    @State var alertIsPresented = false
    
    var alertTitle = ""
    var alertMessage = ""
    
    @EnvironmentObject var loginViewModel: AuthViewModel
    @EnvironmentObject var authentication: Authentication
    @EnvironmentObject var personaViewModel: PersonaViewModel
    @EnvironmentObject var contactViewModel: ContactViewModel
    @EnvironmentObject var chatViewModel: ChatViewModel
    
    var body: some View {
        ZStack {
            
            Color("background")
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                if loginViewModel.showProgressView {
                    ProgressView()
                }
                
                HStack {
                    Text("Login")
                        .font(.pageTitle)
                        .foregroundColor(Color("text-primary"))
                    
                    Spacer()
                    
                    Button {
                        showingAddPersona = true
                    } label: {
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.system(size: 30))
                            .foregroundColor(Color("button-primary"))
                    }
                    .sheet(isPresented: $showingAddPersona) {
                        PersonaAddView(isPresented: $showingAddPersona,
                                       name: name,
                                       subName: subName)
                    }
                    
                }
                .padding([.top, .leading, .trailing])
                
                Divider()
                    .frame(height: 1)
                    .background(Color("text-primary"))
                    .padding([.top, .leading, .trailing])
                
                List {
                    ForEach (personaViewModel.users) { user in
                        GeometryReader { (geometry) in
                            if let persona = personaViewModel.getPersona(uri: user.uri) {
                                VStack(alignment: .leading) {
                                    HStack {
                                        HStack(alignment: .top) {
                                            PersonaRow(user: user,
                                                       status: personaViewModel.getPersonaStatus(persona))
                                        }
                                        .onTapGesture {
                                            doLogin(persona)
                                        }
                                        .disabled(personaViewModel.shouldDisablePersona(persona))
                                        .foregroundColor(personaViewModel.shouldDisablePersona(persona)
                                                         ? .gray : .blue)
                                        .frame(width: geContentWidth(geometryWidth: geometry.size.width - 10))
                                        
                                        NavigationLink("", destination: PersonaDetailView(persona: persona, user: user))
                                    }
                                }
                                .swipeActions(edge: .leading) {
                                    Button() {
                                        guard let persona = personaViewModel.getPersona(uri: user.uri) else {
                                            
                                            log.error("Error getting persona for: \(user.getName())")
                                            return
                                        }
                                        
                                        name = user.firstName ?? ""
                                        subName = user.lastName ?? ""
                                        selectedPersona = persona
                                        showingEditPersona = true
                                    } label: {
                                        Label("Edit", systemImage: "highlighter")
                                    }
                                    .tint(.blue)
                                }
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        personaViewModel.deletePersona(user)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                                .sheet(isPresented: $showingEditPersona) {
                                    PersonaEditView(isPresented: $showingEditPersona,
                                                    name: name,
                                                    subName: subName,
                                                    selectedPersona: $selectedPersona)
                                }

                            } else {
                                VStack(alignment: .leading) {
                                    HStack {
                                        HStack(alignment: .top) {
                                            PersonaRow(user: user,
                                                       status: personaViewModel.getPersonaStatus(status: PersonaStatus(rawValue: user.status!) ?? .unknown))
                                        }
                                        .disabled(true)
                                        .foregroundColor(.gray)
                                        .frame(width: geContentWidth(geometryWidth: geometry.size.width))
                                    }
                                }
                            }
                        }
                        .frame(height: 50)
                    }
                }
                .listRowBackground(Color.white)
                .listStyle(.plain)
            }
            .disabled(loginViewModel.showProgressView)
            .onAppear(perform: {
                authentication.loginListener = chatViewModel
                personaViewModel.onViewAppeared()
                chatViewModel.onViewDisappeared()
                chatViewModel.closeMqttChannel()
            })
            .onDisappear(perform: {
                personaViewModel.onViewDisappeared()
            })
        }
//        .sheet(isPresented: $isProgressShowing) {
//            PersonaProgressView()
//        }

    }
    
    fileprivate func doLogin(_ persona: Persona) {
        chatViewModel.reset()
        loginViewModel.persona = persona
        loginViewModel.setActivePersona { success in
            authentication.updateValidation(success)
            
            if !success {
                return
            }
            
            contactViewModel.contacts.removeAll()
            chatViewModel.contactViewModel = contactViewModel
            chatViewModel.loadContacts()
        }
    }
    
    fileprivate func geContentWidth(geometryWidth: CGFloat) -> CGFloat {
        return geometryWidth - 20
    }
}

struct PersonaListView_Previews: PreviewProvider {
    static var previews: some View {
        PersonaListView(isProgressShowing: .constant(false))
            .environmentObject(PersonaViewModel())
    }
}

extension PersonaListView : TypeNameDescribable {}
