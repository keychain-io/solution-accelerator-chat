using Keychain;
using System;
using System.ComponentModel.DataAnnotations;

namespace KeychainChat.MVVM.Model.Chat
{
    public class User
    {
        [Key]
        public string Id { get; set; } = Guid.NewGuid().ToString();

        public string FirstName { get; set; }
        public string LastName { get; set; }

        public int Status { get; set; }

        public string? Photo { get; set; }   // For later, maybe

        public string? Uri { get; set; }

        public string GetName()
        {
            string name = (FirstName != null ? FirstName : "") + " " + (LastName != null ? LastName : "");
            return name;
        }

        public string GetKey()
        {
            if (Status != (int)PersonaStatus.Confirmed || string.IsNullOrEmpty(Uri) || Uri.Length < 100)
            {
                return GetName();
            }

            return Uri;
        }
    }
}
