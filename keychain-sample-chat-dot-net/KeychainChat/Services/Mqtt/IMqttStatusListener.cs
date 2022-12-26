using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using KeychainChat.Services.Channel;

namespace KeychainChat.Services.Mqtt
{
    public interface IStatusListener
    {
        public void OnConnectionStatusChange(object? source, EventArgs? status);
    }
}
