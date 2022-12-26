using System;
using System.Collections.Generic;
using System.Threading;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Configuration;

using MQTTnet;
using MQTTnet.Protocol;

using NLog;
using KeychainChat.Interfaces;
using KeychainChat.Services.Mqtt;
using MQTTnet.Client;
using System.Windows.Interop;
using System.Diagnostics;

namespace KeychainChat.Services.Channel
{
    internal class MqttChannel : IChannel
    {
        private const string TAG = "MqttChannel";
        private const string OPENING_CHANNEL = "Opening MQTT Channel";
        private const string PUBLISHER_THREAD = "MQTT Publisher Thread";
        private const string CONSUMER_THREAD = "MQTT Consumer Thread";
        private const string PUBLISHER_ALREADY_RUNNING = "Publisher thread already running.";
        private const string PUBLISHER_THREAD_STARTED = "MQTT publisher thread started.";
        private const string CONSUMER_THREAD_STARTED = "MQTT consumer thread started.";
        private const string PUBLISHER_QUEUE_MESSAGE_COUNT = "MQTT publisher queue contains {0} messages.";
        private const string CONSUMER_QUEUE_MESSAGE_COUNT = "MQTT consumer queue contains {0} messages.";
        private const string ERROR_PUBLISHING = "Error publishing messages: ";
        private const string PUBLISHER_THREAD_STOPPED = "MQTT publisher thread was stopped.";
        private const string ERROR_CONSUMING_MESSAGE = "Error consuming message: ";
        private const string ERROR_SUBSCRIBING = "Error subscribing to topics: ";
        private const string RECEIVED_MESSAGE = "Received message from source: ";
        private const string STATUS_CHANGED = "MQTT status changed to: ";
        private const string PUBLISHING_MESSAGE = "Publishing message to: ";
        private const string MQTT_CLIENT_IS_NULL = "mqttClient is null";
        private const string SUBSCRIBING = "Subscribing to topic: ";
        private const string ERROR_DISCONNECTING = "Exception disconnecting from mqtt: ";
        private const string CONNECTING_TO_MQTT = "Connecting to MQTT host: {0}, port: {1}";
        private const string ERROR_PROCESSING_RECEIVED_MESSAGE = "Error processing received message: ";
        private const string PUBLISHING_STATUS_UPDATE = "Publishing status update to ";
        private const string SUCCESSFULLY_CONNECTED = "Successfully connected to broker.";
        private const string UNSUBSCRIBING = "Unsubscribing from topic: ";

        public const string ERROR_CONNECTING = "Couldn't connect to broker.";
        private const string SUCCESSFULLY_RECONNECTED = "Successfully reconnected";
        private const string MQTT_DISCONNECTED = "MQTT disconnected";
        private const string ERROR_RECONNECTING = "Error attempting to reconnect to MQTT";
        private const string MQTT_HOST = "MqttHost";
        private const string MQTT_PORT = "MqttPort";
        private const string CONNECT_TIMEOUT = "MqttConnectTimeout";
        private const string KEEP_ALIVE_SECONDS = "MqttKeepAliveSeconds";

        private readonly Logger log = LogManager.GetCurrentClassLogger();

        private readonly ISet<string> topics = new HashSet<string>();

        // Outgoing messages queue
        private readonly Queue<MqttChannelMessage> outQueue = new();

        // Incomming messages queue
        private readonly Queue<MqttChannelMessage> inQueue = new();

        private bool shouldStop = false;
        private bool publisherRunning = false;
        private readonly AutoResetEvent messageSendEvent = new(false);
        private readonly AutoResetEvent messageArrivedEvent = new(false);

        private readonly string host;
        private readonly int port;
        private ConnectionStatus status;

        private MqttClientOptions connectOptions;
        private IMqttClient mqttClient;
        private MqttFactory mqttFactory;
        private int connectTimeout;
        private double keepAliveSeconds;

        public IMqttClient MqttClient { get => mqttClient; }

        private EventHandler StatusChanged;

        private EventHandler MessageReceived;

        public MqttChannel(IMqttMessageHandler messageHandler)
        {
            Debug.WriteLine(OPENING_CHANNEL);

            MessageReceived += messageHandler.OnReceiveMqttMessage;

            host = ConfigurationManager.AppSettings.Get(MQTT_HOST);
            port = int.Parse(ConfigurationManager.AppSettings.Get(MQTT_PORT));
            connectTimeout = int.Parse(ConfigurationManager.AppSettings.Get(CONNECT_TIMEOUT));
            keepAliveSeconds = double.Parse(ConfigurationManager.AppSettings.Get(KEEP_ALIVE_SECONDS));

            string clientId = Guid.NewGuid().ToString();

            // Create client connectOptions objects
            connectOptions = new MqttClientOptionsBuilder()
                                                    .WithClientId(clientId)
                                                    .WithTcpServer(host, port)
                                                    .WithCleanSession()
                                                    .WithWillRetain(false)
                                                    .WithWillQualityOfServiceLevel(MqttQualityOfServiceLevel.ExactlyOnce)
                                                    .WithKeepAlivePeriod(TimeSpan.FromSeconds(keepAliveSeconds))
                                                    .Build();

            mqttFactory = new MqttFactory();
        }

        public void Initialize() {
            // Creates the client object
            mqttClient = mqttFactory.CreateMqttClient();

            //--- Set up handlers ---
            mqttClient.DisconnectedAsync += async args =>
            {
                Debug.WriteLine(MQTT_DISCONNECTED);
                SetStatus(ConnectionStatus.DISCONNECTED);

                if (args.ClientWasConnected && !shouldStop)
                {
                    try
                    {
                        // Starts a connection with the Broker
                        using (var timeoutToken = new CancellationTokenSource(TimeSpan.FromSeconds(connectTimeout)))
                        {
                            await mqttClient.ConnectAsync(mqttClient.Options, timeoutToken.Token);

                            Debug.WriteLine(SUCCESSFULLY_RECONNECTED);

                        }
                    }
                    catch (Exception e)
                    {
                        Debug.WriteLine(ERROR_RECONNECTING, e);
                    }
                }
            };

            mqttClient.ConnectingAsync += args =>
            {
                Debug.WriteLine(CONNECTING_TO_MQTT);
                SetStatus(ConnectionStatus.CONNECTING);

                return Task.CompletedTask;
            };

            mqttClient.ConnectedAsync += args =>
            {
                if (args == null)
                    return Task.CompletedTask;

                MqttClientConnectResult result = args.ConnectResult;

                if (result.ResultCode == MqttClientConnectResultCode.Success)
                {
                    Debug.WriteLine(SUCCESSFULLY_CONNECTED);

                    SetStatus(ConnectionStatus.CONNECTED);
                }

                return Task.CompletedTask;
            };

            mqttClient.ApplicationMessageReceivedAsync += args =>
            {
                try
                {
                    var topic = args.ApplicationMessage.Topic;
                    string message = args.ApplicationMessage.ConvertPayloadToString();

                    Debug.WriteLine(RECEIVED_MESSAGE, topic);
                    Debug.WriteLine(message);

                    OnReceive(topic, message);
                }
                catch (Exception e)
                {
                    Debug.WriteLine(ERROR_PROCESSING_RECEIVED_MESSAGE, e);
                }

                return Task.CompletedTask;
            };

            status = ConnectionStatus.DISCONNECTED;
            PublishStatusUpdate();

            Thread publisher = new(PublisherThread)
            {
                Name = PUBLISHER_THREAD
            };
            publisher.Start();

            Thread consumer = new(ConsumerThread)
            {
                Name = CONSUMER_THREAD
            };
            consumer.Start();
        }

        private void SetStatus(ConnectionStatus status)
        {
            this.status = status;
            PublishStatusUpdate();
        }

        private void PublishStatusUpdate()
        {
            log.Debug(TAG, PUBLISHING_STATUS_UPDATE + status);

            if (StatusChanged != null)
            {
                var args = new ConnectionStatusEventArgs()
                {
                    ConnectionStatus = status
                };

                StatusChanged(this, args);
            }
        }

        private void PublisherThread()
        {
            if (publisherRunning)
            {
                Debug.WriteLine(PUBLISHER_ALREADY_RUNNING);
                return;
            }

            publisherRunning = true;

            Debug.WriteLine(PUBLISHER_THREAD_STARTED);

            try
            {
                while (!shouldStop)
                {
                    try
                    {
                        Debug.WriteLine(PUBLISHER_QUEUE_MESSAGE_COUNT, outQueue.Count);

                        messageSendEvent.WaitOne(-1);

                        if (outQueue.Count == 0)
                        {
                            Thread.Sleep(500);
                            continue;
                        }

                        Debug.WriteLine(PUBLISHER_QUEUE_MESSAGE_COUNT, outQueue.Count);

                        MqttChannelMessage msg;

                        lock (outQueue)
                        {
                            msg = outQueue.Dequeue();
                        }

                        if (msg == null)
                        {
                            continue;
                        }

                        _ = Task.Run(async () => await PublishAsync(msg.Destination, msg.Message));
                    }
                    catch (Exception e)
                    {
                        Debug.WriteLine(ERROR_PUBLISHING, e);
                    }
                }
            }
            finally
            {
                Debug.WriteLine(PUBLISHER_THREAD_STOPPED);
                publisherRunning = false;
            }
        }

        private void ConsumerThread()
        {
            Debug.WriteLine(CONSUMER_THREAD_STARTED);

            while (!shouldStop)
            {
                try
                {
                    messageArrivedEvent.WaitOne(-1);

                    Debug.WriteLine(CONSUMER_QUEUE_MESSAGE_COUNT, inQueue.Count);

                    while (inQueue.Count > 0)
                    {
                        var message = inQueue.Dequeue();

                        var args = new MessageRecievedEventArgs()
                        {
                            Source = message.Destination,
                            Message = message.Message
                        };

                        MessageReceived(this, args);
                    }
                }
                catch (Exception e)
                {
                    Debug.WriteLine(ERROR_CONSUMING_MESSAGE, e);
                }
            }
        }

        public void Subscribe(IList<string> subscriptions)
        {
            try
            {
                foreach (string subscription in subscriptions)
                {
                    topics.Add(subscription);
                    Subscribe(subscription);
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_SUBSCRIBING, e);
                throw;
            }
        }

        public void Subscribe(string topic)
        {
            try
            {
                if (MqttClient == null)
                {
                    Debug.WriteLine(MQTT_CLIENT_IS_NULL);
                }

                Debug.WriteLine(SUBSCRIBING + topic);

                var result = MqttClient
                    .SubscribeAsync(topic, MqttQualityOfServiceLevel.ExactlyOnce, CancellationToken.None)
                    .GetAwaiter()
                    .GetResult();
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_SUBSCRIBING + topic, e);
                throw;
            }
        }

        public void Send(string destination, string message)
        {
            lock(outQueue)
            {
                outQueue.Enqueue(new MqttChannelMessage(destination, message));
                messageSendEvent.Set();
            }
        }

        public void OnReceive(string source, string message)
        {
            Debug.WriteLine(RECEIVED_MESSAGE + source);

            lock(inQueue)
            {
                inQueue.Enqueue(new MqttChannelMessage(source, message));
                messageArrivedEvent.Set();
            }
        }

        private async Task PublishAsync(string destination, string message)
        {
            try
            {
                Debug.WriteLine($"{PUBLISHING_MESSAGE}{destination}\n{message}");

                MqttClientPublishResult result = await MqttClient.PublishStringAsync(
                    destination,
                    message,
                    MqttQualityOfServiceLevel.ExactlyOnce,
                    true);
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_PUBLISHING, e);
            }
        }

        public async void DisconnectAsync()
        {
            try
            {
                var mqttClientDisconnectOptions = mqttFactory.CreateClientDisconnectOptionsBuilder().Build();
                await MqttClient.DisconnectAsync(
                    mqttClientDisconnectOptions,
                    CancellationToken.None);
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_DISCONNECTING, e);
            }
        }

        public async Task<MqttClientConnectResult> ConnectAsync()
        {
            try
            {
                Debug.WriteLine(CONNECTING_TO_MQTT, host, port);

                // Starts a connection with the Broker
                using (var timeoutToken = new CancellationTokenSource(TimeSpan.FromSeconds(connectTimeout)))
                {
                    var result = await MqttClient.ConnectAsync(connectOptions, timeoutToken.Token);

                    if (result != null && result.ResultCode == MqttClientConnectResultCode.Success)
                    {
                        Debug.WriteLine(SUCCESSFULLY_CONNECTED);
                    }
                    else
                    {
                        Debug.WriteLine(ERROR_CONNECTING);
                    }

                    return result;
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_CONNECTING, e);
                throw;
            }
        }

        public async void Unsubscribe(string topic)
        {
            try
            {
                Debug.WriteLine(UNSUBSCRIBING + topic);

                _ = await MqttClient?.UnsubscribeAsync(topic);
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_CONNECTING, e);

            }
        }


        public void OnStatusChange(ConnectionStatus status)
        {
            Debug.WriteLine(STATUS_CHANGED + status.ToString());

            PublishStatusUpdate();
        }

        public void Close()
        {
            try
            {
                foreach (string topic in topics)
                {
                    Unsubscribe(topic);
                }
            }
            finally
            {
                DisconnectAsync();
                shouldStop = true;
            }
        }

        public void AddStatusListener(IStatusListener mqttStatusListener)
        {
            StatusChanged += mqttStatusListener.OnConnectionStatusChange;
        }

        public void RemoveStatusListener(IStatusListener mqttStatusListener)
        {
            if (StatusChanged != null)
            {
                StatusChanged -= mqttStatusListener.OnConnectionStatusChange;
            }
        }

        public void AddMessageListener(IMqttMessageHandler messageListener)
        {
            MessageReceived += messageListener.OnReceiveMqttMessage;
        }

        public void RemoveMessageListener(IMqttMessageHandler messageListener)
        {
            if (MessageReceived != null)
            {
                MessageReceived -= messageListener.OnReceiveMqttMessage;
            }
        }
    }
}
