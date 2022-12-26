//
//  ContactDetailView.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 8/29/21.
//

import SwiftUI

import Logging

struct ContactDetailView: View {
    let log = Logger(label: ContactDetailView.typeName)
    
    var contact: Contact
    
    @EnvironmentObject var contactViewModel: ContactViewModel
    @EnvironmentObject var keychainViewModel: ChatViewModel
    
    var body: some View {
        VStack(alignment: .leading) {
            Divider()
            
            VStack(alignment: .center) {
                getQRCodeView()
            }
            .padding(.horizontal)
            
            VStack(alignment: .leading, spacing: 13.0) {
                HStack(spacing: 13.0) {
                    Text(NSLocalizedString("Name", comment: ""))
                        .fontWeight(.bold)
                    Text(contactViewModel.getName(contact))
                }
                .padding([.leading, .bottom, .trailing])
                
                HStack(alignment: .top, spacing: 13.0) {
                    Text(NSLocalizedString("URI", comment: ""))
                        .fontWeight(.bold)
                    Text(contactViewModel.getUriString(contact))
                }
            }
            .padding()
        }
        .accentColor(.blue)
        .navigationTitle("Contact")
        .onAppear(perform: {
            keychainViewModel.onViewDisappeared()
        })
        .onDisappear(perform:{
            keychainViewModel.onViewAppeared()
        })
        
        Spacer()
    }
    
    func getQRCodeView() -> QRCodeView {
        do {
            return try QRCodeView(stringData: contact.getUri().toString())
        } catch {
            log.error("\(error)")
        }
        
        return QRCodeView(stringData: "")
    }
    
    func getName() -> String {
        do {
            return try "\(contact.getName())"
        } catch {
            log.error("\(error)")
        }
        
        return ""
    }
}

extension ContactDetailView : TypeNameDescribable {}

struct ContactDetailView_Previews: PreviewProvider {
    static var previews: some View {
        // Create a dummy contact view model and pass it into the detail view so that we can see a preview
        let viewModel = ChatViewModel()
        
        ContactDetailView(contact: viewModel.contacts[0])
    }
}
