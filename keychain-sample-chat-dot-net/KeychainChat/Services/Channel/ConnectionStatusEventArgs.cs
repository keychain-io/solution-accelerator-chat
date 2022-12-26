using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.Services.Channel
{
    public class ConnectionStatusEventArgs : EventArgs
    {
        public ConnectionStatus ConnectionStatus { get; set; }
    }
}
