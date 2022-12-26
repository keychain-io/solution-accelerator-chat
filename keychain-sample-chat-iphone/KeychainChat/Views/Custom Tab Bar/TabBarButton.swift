//
//  TabBarButton.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/05/30.
//

import SwiftUI

struct TabBarButton: View {
    var buttonText: String
    var imageName: String
    var isActive: Bool
    
    var body: some View {
        VStack {
            GeometryReader { geo in
                if isActive {
                    Rectangle()
                        .foregroundColor(.blue)
                        .frame(width: geo.size.width/2, height: 4)
                        .padding(.leading, geo.size.width/4)
                }

                VStack (alignment: .center, spacing: 4) {
                    Image(systemName: imageName)
                        .resizable()
                        .scaledToFit()
                        .foregroundColor(Color("button-primary"))
                        .frame(width: 24, height: 24)
                    
                    Text(buttonText)
                        .foregroundColor(Color("text-primary"))
                        .font(.tabBar)
                }
                .frame(width: geo.size.width, height: geo.size.height)
            }
        }
    }
}

struct TabBarButton_Previews: PreviewProvider {
    static var previews: some View {
        TabBarButton(buttonText: "My Button", imageName: "plus.bubble.fill", isActive: true)
    }
}
