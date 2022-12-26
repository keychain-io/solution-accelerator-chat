//
//  GrayButton.swift
//  ConsumerApp
//
//  Created by Robert Ellis on 9/12/21.
//

import SwiftUI

struct GrowingButton: ButtonStyle {
    var backgroundColor = UIColor.systemGray3
    
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding()
            .background(Color(backgroundColor))
            .foregroundColor(.black)
            .clipShape(RoundedRectangle(cornerSize: CGSize(width: 5, height: 5)))
            .scaleEffect(configuration.isPressed ? 1.2 : 1)
            .animation(.easeOut(duration: 0.2), value: configuration.isPressed)
    }
}
