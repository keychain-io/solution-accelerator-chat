using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Threading.Tasks;
using KeychainChat.Interfaces;
using KeychainChat.MVVM.ViewModel;
using KeychainChat.Services.Channel;
using KeychainChat.Services.Mqtt;

using NLog;

namespace KeychainChat.Services
{
    public class MqttService
    {
        private const string TAG = "MqttService";
        private const string ERROR_SUBSCRIBING_MULTIPLE = "Exception subscribing to multiple topics: ";
        private const string CHANNEL_IS_NULL = "Cannot subscribe to topics, channel is null.";
        private const string INITIALIZING_MQTT = "Initializing MQTT";
        private const string ERROR_INITIALIZING = "Exception initializing MQTT service: ";

        private readonly Logger log = LogManager.GetCurrentClassLogger();
        private IChannel channel;
        private static MqttService mqttService;
        private static object lockObject = new object();

        private MqttService(IChannel channel)
        {
            this.channel = channel;
        }

        public static MqttService GetInstance(IMqttMessageHandler messageHandler)
        {
            lock (lockObject)
            {
                if (mqttService == null)
                {
                    mqttService = new MqttService(new MqttChannel(messageHandler));
                }
            }

            return mqttService;
        }

        public void InitializeMqtt()
        {
            try
            {
                log.Debug(TAG, INITIALIZING_MQTT);

                channel.Initialize();
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_INITIALIZING, e);
            }
        }

        public void AddStatusListener(IStatusListener mqttStatusListener)
        {
            if (channel == null)
            {
                Debug.WriteLine(CHANNEL_IS_NULL);
                return;
            }

            channel.AddStatusListener(mqttStatusListener);
        }

        public void RemoveStatusListener(IStatusListener mqttStatusListener)
        {
            if (channel == null)
            {
                Debug.WriteLine(CHANNEL_IS_NULL);
                return;
            }

            channel.RemoveStatusListener(mqttStatusListener);
        }

        public void AddMessageListener(IMqttMessageHandler messageListener)
        {
            if (channel == null)
            {
                Debug.WriteLine(CHANNEL_IS_NULL);
                return;
            }

            channel.AddMessageListener(messageListener);
        }

        public void RemoveStatusListener(IMqttMessageHandler messageListener)
        {
            if (channel == null)
            {
                Debug.WriteLine(CHANNEL_IS_NULL);
                return;
            }

            channel.RemoveMessageListener(messageListener);
        }

        public void Subscribe(IList<string> topics)
        {
            try
            {
                if (channel == null)
                {
                    Debug.WriteLine(CHANNEL_IS_NULL);
                    return;
                }

                channel.Subscribe(topics);
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_SUBSCRIBING_MULTIPLE, e);
            }
        }

        public void Unsubscribe(string topic)
        {
            if (channel == null)
            {
                return;
            }

            channel.Unsubscribe(topic);
        }

        public void Disconnect()
        {
            if (channel == null)
            {
                Debug.WriteLine(CHANNEL_IS_NULL);
                return;
            }

            channel.DisconnectAsync();
        }

        public async Task ConnectAsync()
        {
            if (channel == null)
            {
                Debug.WriteLine(CHANNEL_IS_NULL);
                return;
            }

            _ = await channel.ConnectAsync();
        }

        public void Send(string destination, string message)
        {
            if (channel == null)
            {
                Debug.WriteLine(CHANNEL_IS_NULL);
                return;
            }

            channel.Send(destination, message);
        }

        public void Close()
        {
            if (channel == null)
            {
                return;
            }

            channel.Close();
        }
    }
}
