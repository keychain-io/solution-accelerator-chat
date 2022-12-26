//
//  PersonaProgressView.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/06/06.
//

import SwiftUI

struct PersonaProgressView: View {
    var body: some View {
        VStack {
            ProgressView("Creating Persona")
        }
    }
}

struct PersonaProgressView_Previews: PreviewProvider {
    static var previews: some View {
        PersonaProgressView()
    }
}
