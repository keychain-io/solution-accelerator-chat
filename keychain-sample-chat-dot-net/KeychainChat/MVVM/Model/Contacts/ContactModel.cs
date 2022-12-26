using KeychainChat.MVVM.Model.Chat;
using KeychainChat.MVVM.Model.Message;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.MVVM.Model.Contact
{
    public class ContactModel
    {
        public string Uri { get; set; }

        public string Name { get; set; }

        public string FirstName { get; internal set; }
        public string LastName { get; internal set; }

        public string Initials
        {
            get
            {
                var first = !string.IsNullOrWhiteSpace(FirstName)
                    ? $"{FirstName[0]}" : "";
                var second = !string.IsNullOrWhiteSpace(LastName)
                    ? $"{LastName[0]}" : "";

                return $"{first}{second}";
            }
        }

        public ObservableCollection<MessageModel> ChatMessages { get; set; } = new ObservableCollection<MessageModel>();

        public string LastMessage => (ChatMessages != null && ChatMessages.Count > 0) 
            ? ChatMessages.Last().Message 
            : string.Empty;
    }
}
