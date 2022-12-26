using System;
using System.ComponentModel.DataAnnotations;

namespace KeychainChat.MVVM.Model.Chat
{
    public class Chat
    {
        [Key]
        public string Id { get; set; } = Guid.NewGuid().ToString();

        public string ParticipantIds { get; set; }

        public string LastMsg { get; set; }

        public DateTime Timestamp { get; set; } = DateTime.Now;
    }
}
