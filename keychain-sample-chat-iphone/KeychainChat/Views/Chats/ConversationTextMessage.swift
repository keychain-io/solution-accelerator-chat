import SwiftUI

struct ConversationTextMessage: View {
    var msg: String
    var name: String
    var isFromMe: Bool
    var isSendToAllContacts: Bool

    var body: some View {
        
        VStack(alignment: .leading) {
            if isSendToAllContacts && !isFromMe {
                Text(name)
                    .font(Font.sender)
                    .foregroundColor(.red)
            }
            
            Text(msg)
                .font(Font.body)
                .foregroundColor(isFromMe ? Color("text-primary") : Color("text-primary"))
        }
        .padding(.vertical, 16)
        .padding(.horizontal, 24)
        .background(isFromMe ? Color("bubble-primary") : Color("bubble-secondary"))
        .cornerRadius(30, corners: isFromMe ? [.topLeft, .topRight, .bottomLeft] : [.topLeft, .topRight, .bottomRight])
    }
}

struct ConversationTextMessage_Previews: PreviewProvider {
    static var previews: some View {
        ConversationTextMessage(msg: "Test", name: "Chucky", isFromMe: true, isSendToAllContacts: false)
    }
}
