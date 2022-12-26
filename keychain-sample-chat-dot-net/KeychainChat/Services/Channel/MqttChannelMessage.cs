using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.Services.Channel
{
    public class MqttChannelMessage
    {
        private string destination;
        private string message;

        public MqttChannelMessage(string destination, string message)
        {
            Destination = destination;
            Message = message;
        }

        public string Destination { get => destination; set => destination = value; }
        public string Message { get => message; set => message = value; }
    }
}
