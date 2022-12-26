using KeychainChat.Services.Channel;
using KeychainChat.Services.Mqtt;
using MQTTnet.Client;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.Interfaces
{
    public interface IChannel
    {
        void Initialize();

        Task<MqttClientConnectResult> ConnectAsync();

        void DisconnectAsync();

        public void Subscribe(IList<string> subscriptions);

        void Unsubscribe(string topic);

        public void Send(string destination, string message);

        public void OnReceive(string source, string message);

        public void OnStatusChange(ConnectionStatus status);

        public void Close();

        public void AddStatusListener(IStatusListener mqttStatusListener);

        public void RemoveStatusListener(IStatusListener mqttStatusListener);
        
        public void AddMessageListener(IMqttMessageHandler messageListener);
        
        public void RemoveMessageListener(IMqttMessageHandler messageListener);
    }
}
