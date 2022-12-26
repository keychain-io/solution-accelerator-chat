import SwiftUI

struct ConversationPhotoMessage: View {
    
    var imageUrl: String
    var isFromMe: Bool
    var isSendToAllContacts: Bool
    var name: String

    var body: some View {
        
        // Check image cache, if it exists, use that
        if let cachedImage = ImageCacheService.getImage(forKey: imageUrl) {
            
            // Image is in cache so lets use it
            cachedImage
                .resizable()
                .scaledToFill()
                .padding(.vertical, 16)
                .padding(.horizontal, 24)
                .background(isFromMe ? Color("bubble-primary") : Color("bubble-secondary"))
                .cornerRadius(30, corners: isFromMe ? [.topLeft, .topRight, .bottomLeft] : [.topLeft, .topRight, .bottomRight])
        }
        else {
            
            // Download the image
            
            // Create URL from msg photo url
            let photoUrl = URL(string: imageUrl)
            
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
                            .scaledToFill()
                            .padding(.vertical, 16)
                            .padding(.horizontal, 24)
                            .background(isFromMe ? Color("bubble-primary") : Color("bubble-secondary"))
                            .cornerRadius(30, corners: isFromMe ? [.topLeft, .topRight, .bottomLeft] : [.topLeft, .topRight, .bottomRight])
                            .onAppear {
                                
                                // Save this image to cache
                                ImageCacheService.setImage(image: image, forKey: imageUrl)
                            }
                        
                    case .failure:
                        // Couldn't fetch profile photo
                        // Display circle with first letter of first name
                        ConversationTextMessage(msg: "Couldn't load image",
                                                name: name,
                                                isFromMe: isFromMe,
                                                isSendToAllContacts: isSendToAllContacts)
                    @unknown default:
                        ProgressView()
                }
            }
        }
        
    }
}

struct ConversationPhotoMessage_Previews: PreviewProvider {
    static var previews: some View {
        ConversationPhotoMessage(imageUrl: "", isFromMe: true, isSendToAllContacts: false, name: "Larry")
    }
}
