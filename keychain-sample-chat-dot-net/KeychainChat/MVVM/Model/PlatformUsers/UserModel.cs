using Keychain;
using KeychainChat.MVVM.Model.Chat;

namespace KeychainChat.MVVM.Model.PlatformUser
{
    public class UserModel
    {
        public string Uri { get; set; }

        public string Name { get; set; }

        public string FirstName { get; set; }
        public string LastName { get; set; }

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

        public PersonaStatus Status { get; set; }

        public string StatusString => Status.ToString();

        public string StatusColor
        {
            get
            {
                switch (Status)
                {
                    case PersonaStatus.Created:
                        return "Black";
                    case PersonaStatus.Funding:
                    case PersonaStatus.Broadcasted:
                    case PersonaStatus.Confirming:
                        return "Orange";
                    case PersonaStatus.Confirmed:
                        return "#6D6E71";
                    case PersonaStatus.Expiring:
                    case PersonaStatus.Expired:
                    case PersonaStatus.Unrecognized:
                    default:
                        return "Red";
                }
            }
        }

        public UserModel(User user)
        {
            Uri = user.Uri;
            Name = user.GetName();
            FirstName = user.FirstName;
            LastName = user.LastName;
            Status = (PersonaStatus)user.Status;
        }
    }
}
