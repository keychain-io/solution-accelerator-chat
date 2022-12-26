//
//  PersonaRowView.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/06/05.
//

import SwiftUI

struct PersonaRow: View {
    var user: User
    var status: Text
    
    var body: some View {
        HStack (spacing: 12) {
            
            // Profile Image
            ProfilePicView(user: user, backgroundColor: Color("circle-persona"), textColor: Color("circle-persona-text"))
            
            VStack (alignment: .leading) {
                // Name
                Text("\(user.firstName ?? "") \(user.lastName ?? "")")
                    .font(.headline)
                    .foregroundColor(Color("text-primary"))
            }
            
            // Extra space
            Spacer()
            
            status
        }
    }
}

struct PersonaRowView_Previews: PreviewProvider {
    static var previews: some View {
        ContactRow(user: User())
    }
}
