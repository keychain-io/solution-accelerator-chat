using KeychainChat.Services.Channel;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.Services.Mqtt
{
    public interface IMqttMessageHandler
    {
        public void OnReceiveMqttMessage(object source, EventArgs message);
    }
}
