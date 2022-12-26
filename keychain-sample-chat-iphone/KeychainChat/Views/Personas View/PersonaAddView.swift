//
//  PersonaAddView.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 10/17/21.
//

import SwiftUI

import Logging

struct PersonaAddView: View {
    let log = Logger(label: PersonaAddView.typeName)
    
    @EnvironmentObject var personaViewModel: PersonaViewModel
    
    @Binding var isPresented: Bool
    
    @State var name: String
    @State var subName: String
    
    @State var alertIsPresented = false
    @State var alertTitle = ""
    @State var alertMessage = ""
    
    @State var selectedImage: UIImage?
    @State var isPickerShowing = false
    
    @State var isSourceMenuShowing = false
    @State var source: UIImagePickerController.SourceType = .photoLibrary

    @State var isSaveEnabled = false
    
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

            Text(Constants.addPersona)
                .font(.title)
            
            Spacer()
            
            // Profile image button
            Button {
                
                // Show action sheet
                isSourceMenuShowing = true
                
            } label: {
                
                ZStack {
                    
                    if selectedImage != nil {
                        Image(uiImage: selectedImage!)
                            .resizable()
                            .scaledToFill()
                            .clipShape(Circle())
                    }
                    else {
                        Circle()
                            .foregroundColor(Color("date-pill"))
                        
                        Image(systemName: "camera.fill")
                            .tint(Color("icons-input"))
                    }
                    
                    Circle()
                        .stroke(Color("create-profile-border"), lineWidth: 2)
                    
                }
                .frame(width: 134, height: 134)
                
            }

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

                        personaViewModel.savePersona(firstName: name, lastName: subName, image: selectedImage)
                        
                        name = ""
                        subName = ""
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
        .confirmationDialog("Select Source", isPresented: $isSourceMenuShowing, actions: {
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
            ImagePicker(selectedImage: $selectedImage,
                        isPickerShowing: $isPickerShowing,
                        source: self.source)
        }
    }
}

/*
 struct PersonaAddView_Previews: PreviewProvider {
 @State var id: Int
 
 static var previews: some View {
 PersonaAddView(id: $id)
 }
 }
 */

extension PersonaAddView : TypeNameDescribable {}

