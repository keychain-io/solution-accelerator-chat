import SwiftUI

struct ChatsListRow: View {
    
    @EnvironmentObject var chatViewModel: ChatViewModel

    var chat: Chat
    
    var otherParticipants: [User]?
    
    var body: some View {
        
        HStack (spacing: 24) {
            
            // Assume at least 1 other participant in the chat
            let participant = otherParticipants?.first
            
            // Profile Image of participants
            getProfilePicView(participant)
            
            VStack (alignment: .leading, spacing: 4) {
                getChatName(participant)
                    .font(Font.button)
                    .foregroundColor(Color("text-primary"))
                
                if chat.lastMsg == nil {
                    Text("")
                } else {
                    // last message
                    Text(chatViewModel.getDecryptedMessage(chat.lastMsg!))
                        .font(Font.bodyParagraph)
                        .foregroundColor(Color("text-input"))
                }
            }
            
            // Extra space
            Spacer()
            
            // Timestamp
            Text(chat.timestamp == nil ? "" :
                    DateHelper.chatTimestampFrom(date: chat.timestamp!))
                .font(Font.bodyParagraph)
                .foregroundColor(Color("text-input"))
        }
    }
    
    func getProfilePicView(_ participant: User?) -> ProfilePicView? {
        if participant != nil {
            return ProfilePicView(user: participant!, backgroundColor: Color("circle-contact"), textColor: Color("circle-contact-text"))
        } else if chat.participantIds?.last == Constants.all {
            let user = User()
            user.firstName = Constants.all
            return ProfilePicView(user: user, backgroundColor: Color("circle-contact"), textColor: Color("circle-contact-text"))
        }
        
        return ProfilePicView(user: User(), backgroundColor: Color("circle-contact"), textColor: Color("circle-contact-text"))
    }
    
    fileprivate func getChatName(_ participant: User?) -> Text {
        if chat.participantIds?.last == Constants.all {
            return Text(Constants.all)
        }
        
        guard let participant = participant else {
            return Text("Unknown")
        }

        return Text("\(participant.firstName ?? "") \(participant.lastName ?? "")")
    }
}

