using System.Collections.Generic;
using System.Linq;
using Keychain;
using KeychainChat.MVVM.Model.Chat;

namespace KeychainChat.Interfaces
{
    public interface IChatRepository
    {
       public Dictionary<string, User> GetPlatformUsers(IReadOnlyCollection<string> filterBy);

        public User? GetPlatformUser(string recordId);

        public User? GetPlatformUser(string firstName, string lastName);

        public User? GetPlatformUserByUri(string uri);

        // Returns the record id
        public string? SaveUserProfile(string firstName, string lastName, PersonaStatus status, string uri);

        public bool UpdateUserProfile(string firstName, string lastName, PersonaStatus status, string? uri);

        public Chat? GetChat(string senderId, string receiverId);

        public IList<ChatMessage> GetAllMessages(Chat chat);

        // Returns the record id
        public string? SaveChat(Chat chat);

        public bool UpdateChat(string id, string lastMessage);

        public ChatMessage? GetMessage(string recordId);

        // Returns the record id
        string? SaveMessage(string? chatMsgId, string senderId, string msg, string direction, Chat chat, IEnumerable<byte>? image);

        // Returns the record id
        string? SavePhotoMessage(string senderUri, IEnumerable<byte> image, Chat chat);
        
        void DeletePlatformUser(User user);

        void RenamePlatformUser(User user, string newFirstName, string newLastName);
    }
}
