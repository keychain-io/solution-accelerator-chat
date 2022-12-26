using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.MVVM.Model.Chat
{
    public class ChatMessage
    {
        [Key]
        [JsonProperty("id")]
        public string? Id { get; set; } = Guid.NewGuid().ToString();

        [JsonProperty("chatId")]
        public string? ChatId { get; set; }


        [JsonProperty("sendOrRcvd")]
        public string? SendOrRcvd { get; set; }

        [JsonProperty("senderId")]
        public string? SenderId { get; set; }

        [JsonProperty("receiverId")]
        public string? ReceiverId { get; set; }

        [JsonProperty("imageUrl")]
        public string? ImageUrl { get; set; }    // Not currently used

        [JsonProperty("msg")]
        public string? Msg { get; set; }

        [JsonProperty("timestamp")]
        public long Timestamp { get; set; }
    }
}
