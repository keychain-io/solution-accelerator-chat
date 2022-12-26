//
//  PersonaDetailView.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 8/16/21.
//

import SwiftUI



struct PersonaDetailView: View {
    var persona: Persona
    var user: User
    
    @EnvironmentObject var personaViewModel: PersonaViewModel
    
    var body: some View {
        VStack {
            HStack {
                PersonaRow(user: user, status: personaViewModel.getPersonaStatus(persona))
            }
            .padding(.all)
            
            VStack(alignment: .leading) {
                HStack {
                    Spacer()
                    
                    VStack(alignment: .center) {
                        QRCodeView(stringData: personaViewModel.getQRCodeData(persona: persona))
                    }
                    
                    Spacer()
                }
                .padding([.leading, .bottom, .trailing])
                
                HStack(alignment: .top, spacing: 13.0) {
                    Text(NSLocalizedString("URI", comment: ""))
                        .fontWeight(.bold)
                    Text(personaViewModel.getUriString(persona))
                }
                .padding(.horizontal)
                
                Spacer()
            }
            .navigationTitle(personaViewModel.getName(persona))
            .onAppear(perform: {
                personaViewModel.onViewDisappeared()
            })
            .onDisappear(perform:{
                personaViewModel.onViewAppeared()
            })
        }
        .padding(.all)
    }
}

struct PersonaDetailView_Previews: PreviewProvider {
    static var previews: some View {
        // Create a dummy persona view model and pass it into the detail view so that we can see a preview
        let viewModel = PersonaViewModel()
        
        PersonaDetailView(persona: viewModel.personas[0], user: User())
    }
}
