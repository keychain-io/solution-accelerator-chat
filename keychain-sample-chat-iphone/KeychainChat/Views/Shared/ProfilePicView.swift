//
//  ProfilePicView.swift
//  KeychainChat
//
//  Created by Robert Ellis on 2022/06/08.
//

import SwiftUI

struct ProfilePicView: View {
    var user: User
    var backgroundColor: Color
    var textColor: Color
    
    var body: some View {
        ZStack {
            
            // Check if user has a photo set
            if user.photo == nil {
                
                // Display circle with first letter of first name
                ZStack {
                    Capsule()
                        .fill(backgroundColor)
                        .foregroundColor(backgroundColor)
                        .overlay(Text(user.getInitials())
                            .foregroundColor(textColor)
                        .bold())
                }
                
            }
            else {
                
                // Create URL from user photo url
                let photoUrl = URL(string: user.photo ?? "")
                
                // Profile image
                AsyncImage(url: photoUrl) { phase in
                    
                    switch phase {
                            
                        case .empty:
                            // Currently fetching
                            ProgressView()
                            
                        case .success(let image):
                            // Display the fetched image
                            image
                                .resizable()
                                .clipShape(Circle())
                                .scaledToFill()
                                .clipped()
                            
                        case .failure:
                            // Couldn't fetch profile photo
                            // Display circle with first letter of first name

                            ZStack {
                                Circle()
                                    .foregroundColor(.white)
                                
                                Text(user.firstName?.prefix(1) ?? "")
                                    .bold()
                            }
                            
                        @unknown default:
                            ZStack {
                                Circle()
                                    .foregroundColor(.white)
                                
                                Text(user.firstName?.prefix(1) ?? "")
                                    .bold()
                            }
                    }
                    
                }
                
            }
        }
        .frame(width: 44, height: 44)
    }
}

struct ProfilePicView_Previews: PreviewProvider {
    static var previews: some View {
        ProfilePicView(user: User(), backgroundColor: Color("circle-persona"), textColor: .black)
    }
}
