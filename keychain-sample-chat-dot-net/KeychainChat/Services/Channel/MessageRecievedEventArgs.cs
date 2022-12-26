using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.Services.Channel
{
    public class MessageRecievedEventArgs : EventArgs
    {
        public string Source { get; set; }

        public string Message { get; set; }
    }
}
