//
//  ContactEditView.swift
//  KeychainChat
//
//  Created by Robert Ellis on 11/17/22.
//

import SwiftUI

import Logging

struct ContactEditView: View {
    let log = Logger(label: ContactEditView.typeName)
    
    @EnvironmentObject var contactViewModel: ContactViewModel
    
    @Binding var isPresented: Bool
    
    @State var name: String
    @State var subName: String
    @Binding var selectedContact: Contact
    
    @State var alertIsPresented = false
    @State var alertTitle = ""
    @State var alertMessage = ""
    
    @State var isSaveEnabled = true
    
    var keychainService = KeychainService.instance

    fileprivate func showAlert(title: String, message: String) {
        alertTitle = title
        alertMessage = message
        alertIsPresented = true
        return
    }
    
    fileprivate func setSaveEnabled() {
        isSaveEnabled = !name.isEmpty && !subName.isEmpty
    }
    
    fileprivate func getSaveButtonColor() -> GrowingButton {
        return isSaveEnabled ? GrowingButton(backgroundColor: UIColor.systemBlue) : GrowingButton()
    }
    
    var body: some View {
        VStack {
            
            Spacer()

            Text(Constants.editContact)
                .font(.title)
            
            Spacer()
            
            TextField(Constants.firstNamePrompt, text: $name)
                .padding(.all)
                .border(Color.blue, width: /*@START_MENU_TOKEN@*/1/*@END_MENU_TOKEN@*/)
                .onChange(of: subName, perform: {newValue in
                    setSaveEnabled()
                })

            TextField(Constants.lastNamePrompt, text: $subName)
                .padding(.all)
                .border(Color.blue, width: /*@START_MENU_TOKEN@*/1/*@END_MENU_TOKEN@*/)
                .onChange(of: name, perform: {newValue in
                    setSaveEnabled()
                })
            
            Spacer()
            
            HStack(alignment: .center) {
                Spacer()
                Button("Save", action: {
                    if (name.trimmingCharacters(in: .whitespaces).count > 0 &&
                        subName.trimmingCharacters(in: .whitespaces).count > 0) {

                        isPresented = false

                        contactViewModel.renameContact(contact: selectedContact,
                                                       newName: name,
                                                       newSubName: subName)
                        
                        name = ""
                        subName = ""
                        selectedContact = Contact()
                    } else {
                        log.error("\(Constants.firstAndLastNamesRequired)")
                        
                        if subName.isEmpty {
                            showAlert(title: Constants.errorTitle, message: NSLocalizedString("Last name must not be blank", comment: ""))
                        } else if name.isEmpty {
                            showAlert(title: Constants.errorTitle, message: NSLocalizedString("First name must not be blank", comment: ""))
                        }
                    }
                })
                    .padding(.bottom)
                    .buttonStyle(getSaveButtonColor())
                    .disabled(!isSaveEnabled)
                    .alert(isPresented: $alertIsPresented, content: {
                        Alert(title: Text(alertTitle), message: Text(alertMessage), dismissButton: .default(Text("OK")))
                    })
                
                Spacer()
                
                Button("Cancel") {
                    isPresented = false
                }
                .padding(.bottom)
                .buttonStyle(GrowingButton())
                
                Spacer()
            }
            .foregroundColor(Color("background"))
        }
        .padding(.horizontal)
    }
}

struct ContactEditView_Previews: PreviewProvider {
    static var previews: some View {
        ContactEditView(isPresented: .constant(true),
                        name: "Billy",
                        subName: "Bobby",
                        selectedContact: .constant(Contact()))
    }
}

extension ContactEditView : TypeNameDescribable {}
