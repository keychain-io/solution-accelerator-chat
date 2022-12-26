using System;
using System.Windows;

namespace KeychainChat.MVVM.Model.Message
{
    public class MessageModel
    {
        public string FirstName { get; set; }

        public string LastName { get; set; }

        public string UserName
        {
            get
            {
                return $"{FirstName} {LastName}";
            }
        }

        public string Initials
        {
            get
            {
                string initials =  string.Empty;

                if (!string.IsNullOrWhiteSpace(FirstName))
                {
                    initials = $"{FirstName.Trim()[0]}";
                }

                if (!string.IsNullOrWhiteSpace(LastName))
                {
                    initials += $"{LastName.Trim()[0]}";
                }

                return initials;
            }
        }

        public string Message { get; set; }

        public DateTime Time { get; set; }

        public bool IsFromMe { get; set; }

        public string Alignment
        {
            get
            {
                return IsFromMe ? "Right" : "Left";
            }
        }

        public string InitialsBackgroundColor
        {
            get
            {
                return IsFromMe ? "#BB93DD" : "#FFD0D7";
            }
        }

        public string ChatBackgroundColor
        {
            get
            {
                return IsFromMe ? "#D9EAFC" : "#E9ECFA";
            }
        }

        public string ChatCornerRadius
        {
            get
            {
                return IsFromMe ? "30,30,0,30" : "30,30,30,0";
            }
        }

        public string ChatAllignment
        {
            get
            {
                return IsFromMe ? "Right" : "Left";
            }
        }
    }
}
