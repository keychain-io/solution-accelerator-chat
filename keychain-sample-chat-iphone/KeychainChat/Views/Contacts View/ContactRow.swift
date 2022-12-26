import SwiftUI

struct ContactRow: View {
    
    var user: User
    
    var body: some View {
        HStack (spacing: 24) {
            
            // Profile Image
            ProfilePicView(user: user, backgroundColor: Color("circle-contact"), textColor: Color("circle-contact-text"))
            
            VStack (alignment: .leading, spacing: 4) {
                // Name
                Text("\(user.firstName ?? "") \(user.lastName ?? "")")
                    .font(Font.button)
                    .foregroundColor(Color("text-primary"))
            }
            
            // Extra space
            Spacer()
        }
    }
}

struct ContactRow_Previews: PreviewProvider {
    static var previews: some View {
        ContactRow(user: User())
    }
}
