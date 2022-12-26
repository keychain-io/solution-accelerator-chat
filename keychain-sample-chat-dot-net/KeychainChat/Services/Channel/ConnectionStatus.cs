using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.Services.Channel
{
    public enum ConnectionStatus
    {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        CLOSED,
        UNKNOWN,
    }
}
