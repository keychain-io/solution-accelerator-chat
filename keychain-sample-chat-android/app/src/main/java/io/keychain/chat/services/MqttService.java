package io.keychain.chat.services;

import android.util.Log;

import androidx.annotation.WorkerThread;

import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import io.keychain.mobile.threading.TaskRunner;

/**
 * This is a wrapper for MqttAsyncClient using a single connection.
 * For merchant, this is fine, because there's only 1 activity using MQTT.
 * For other apps, maybe extend it to use multiple MqttAndroidClients and pass in handles to some of the methods below.
 *
 * MQTT is very stateful and very asynchronous.  Users must know exactly what state they are in before taking any actions.
 * For this reason, the repository is set up to assist in 2 ways:
 *    1. All asynchronous calls that return IMqttToken expose it to the callers so they can decide whether to wait on it or not
 *    2. Any time the connection status changes, registered observers are notified on non-blocking threading so they can take action
 * Everyone who uses MQTT should be a status observer, and it is a good idea to have a ViewModel wait on the IMqttTokens in a background
 * thread so you can update the View and let the app user decide whether to retry the action or not.
 */
public class MqttService {
    private static final String TAG = "MqttService";
    private MqttAsyncClient mqttClient;

    MqttConnectOptions options = new MqttConnectOptions();
    private static final int mqttQos = 2;
    private static final long quiesceTimeout = 250;
    private static final int mqttConnectTimeout = 30;
    private static final int mqttKeepAliveSecs = 10;

    // with just 1 client, you can only have 1 subscriber to a topic on the MqttAsyncClient level
    // we could have a layer above (this repository) which subscribes and manages a 1-N publish to others,
    // but generally only 1 ViewModel should be in scope at any time, so it seems like overkill to support that

    // Topic -> Callback code + whether it's directly subscribed yet or not
    private final Map<String, MqttAndroidClientCallbackPair> topicCallbacks;

    private final Set<ConnectionListener> listeners;
    private ConnectionStatus status;
    private final TaskRunner taskRunner;
    private String clientId;
    private final String persistencePath;
    private final String host;
    private final int port;

    public MqttService(String persistencePath, String host, int port) {
        topicCallbacks = new HashMap<>();
        status = ConnectionStatus.CLOSED;
        listeners = new HashSet<>();
        this.persistencePath = persistencePath;
        this.host = host;
        this.port = port;

        // 1 worker thread, so all callbacks are sequential
        taskRunner = new TaskRunner();
    }

    // Status listeners are NOT necessarily subscribers.  You can care about the status without subscribing, say if you only publish
    public ConnectionStatus addStatusListener(ConnectionListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
        return status;
    }

    public void removeStatusListener(ConnectionListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void publishStatusUpdate() {
        Log.d(TAG, "Publishing status update to " + status);
        synchronized(listeners) {
            for(ConnectionListener listener : listeners) {
                taskRunner.executeAsync((Callable<Void>) () -> {
                    listener.onConnectionStatusChange(status);
                    return null;
                }, null);
            }
        }
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void initializeMqtt() {
        Log.d(TAG, "Initializing MQTT");
        if (status != ConnectionStatus.CLOSED || mqttClient != null) {
            Log.w(TAG, "Attempt to initialize instance that is not yet CLOSED state - exiting early");
            return;
        }

        MqttClientPersistence persistence = new MqttDefaultFilePersistence(persistencePath);
        if (clientId == null) {
            clientId = MqttAsyncClient.generateClientId();
        }

        try {
            mqttClient = new MqttAsyncClient("tcp://" + host + ":" + port, clientId, persistence);
        } catch (MqttException e) {
            Log.e(TAG, "Exception creating async client: " + e.getMessage());
            return;
        }

        try {
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    if (reconnect){
                        Log.i(TAG, "Reconnected to : " + serverURI);
                    } else {
                        Log.i(TAG, "Connected to : " + serverURI);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.w(TAG, "Mqtt connection lost: " + cause.getMessage());
                    status = ConnectionStatus.DISCONNECTED;
                    publishStatusUpdate();
                    connectMqtt();
                }

                @Override
                public void messageArrived(String topic, MqttMessage msg) {
                    Log.i(TAG, "Message arrived on topic " + topic);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    Log.i(TAG, "Message delivery complete for token: " + token);
                }
            });

            // set DISCONNECTED only once the client instance is created AND callback set
            status = ConnectionStatus.DISCONNECTED;
            publishStatusUpdate();
        }
        catch (Exception e) {
            Log.e(TAG, "Exception creating mqtt instance: " + e.getMessage());
        }
    }

    /*
    Subscribing adds the callback to the map of topic->callback
    If MQTT is CONNECTED, we will subscribe to it in this method too; otherwise the subscription will happen when it gets connected later
     */
    public IMqttToken subscribeTopic(String topic, MqttAndroidClientCallback callback) {
        Log.i(TAG, "Subscribing to topic " + topic);

        // add topic to the map for safe-keeping
        synchronized (topicCallbacks) {
            // add only if it doesn't exist (topic only - we do not compare callback code, so no overwriting possible)
            if (topicCallbacks.containsKey(topic)) {
                Log.d(TAG, "Skipping subscription to " + topic + " because it's already in the map");
                return null;
            }

            // always add it - even if it's for the same topic, or even if it's a callback (topic+callback code) we already subbed
            topicCallbacks.put(topic, new MqttAndroidClientCallbackPair(callback));

            // subscribe to the client (still in synchronized block)
            return directSubscribeTopic(callback.getTopic()); // this tells the MqttAsyncClient to (re)subscribe to whatever we have in the map for 'topic'
        }
    }

    // NOT thread safe!  Make sure the caller synchronizes topicCallbacks
    private IMqttToken directSubscribeTopic(String topic) {
        if (status != ConnectionStatus.CONNECTED) {
            Log.d(TAG, "Subscription to topic recorded, but will wait until MQTT is CONNECTED state before subscribing");
            return null;
        }

        MqttAndroidClientCallbackPair cbp;
        synchronized (topicCallbacks) {
            // if the callback isn't in the map, or it is but it's 'true' (subbed at MqttAsyncClient level), leave
            if (!topicCallbacks.containsKey(topic)) {
                Log.d(TAG, "Subscription isn't in the map");
                return null;
            }
            if (topicCallbacks.get(topic).directlySubscribed) {
                Log.d(TAG, "No need to subsribe to " + topic + " again, it's already in the server");
                return null;
            }
            cbp = topicCallbacks.get(topic);
            // set to true to prevent multiple attempts
            cbp.directlySubscribed = true;
        }

        try {
            return mqttClient.subscribe(topic, 2, null,
                                        new IMqttActionListener() {
                                            @Override
                                            public void onSuccess(IMqttToken asyncActionToken) {
                                                Log.i(TAG, "subscription to topic: " + topic + " succeeded");
                                            }

                                            @Override
                                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                                Log.w(TAG, "subscription to topic: " + topic + " failed");
                                                cbp.directlySubscribed = false;
                                            }
                                        },
                                        new IMqttMessageListener() {
                                            @Override
                                            public void messageArrived(String topic, MqttMessage message) {
                                                Log.i(TAG, "Message arrived on topic " + topic + " from #subscribe()");
                                                completeMessage(topic, message);
                                                // run async so we get out of here quickly
                                                taskRunner.executeAsync((Callable<Void>) () -> {
                                                    cbp.callback.handleComms(message);
                                                    return null;
                                                }, null);
                                            }
                                        });
        } catch (MqttException e) {
            Log.e(TAG, "Exception subscribing to topic: " + e.getMessage());
            cbp.directlySubscribed = false;
        }
        return null;
    }

    public void unsubscribeTopic(String topic) {
        synchronized (topicCallbacks) {
            topicCallbacks.remove(topic);
        }

        if (status != ConnectionStatus.CONNECTED) {
            Log.d(TAG, "Nothing to be done for unsubscribe; client is not connected");
            return;
        }

        try {
            mqttClient.unsubscribe(topic);
        } catch (MqttException e) {
            Log.e(TAG, "Exception unsubscribing from topic: " + e.getMessage());
        }
    }

    public IMqttToken connectMqtt() {
        Log.i(TAG, "Connecting to mqtt....");
        if (status != ConnectionStatus.DISCONNECTED) {
            Log.w(TAG, "Attempt to connect to client that is not yet in DISCONNECTED state - exiting early");
            return null;
        }

        status = ConnectionStatus.CONNECTING;

        if (mqttClient == null) {
            Log.w(TAG, "mqttClient instance is null - this should never happen");
            status = ConnectionStatus.CLOSED;
        } else if (mqttClient.isConnected()){
            Log.w(TAG, "Already connected to mqtt. Returning.");
            status = ConnectionStatus.CONNECTED;
        }

        publishStatusUpdate();

        // leave now if our status was changed by the checks above
        if (status != ConnectionStatus.CONNECTING) {
            return null;
        }

        try {
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(mqttConnectTimeout);
            options.setKeepAliveInterval(mqttKeepAliveSecs);
            options.setCleanSession(false);

            return mqttClient.connect(options, this, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Mqtt connect successful");
                    status = ConnectionStatus.CONNECTED;

                    // This is OK
                    // #directSubscribeTopic will exit early if already subscribed
                    // and the synchronize lock in #directSubscribeTopic and here are Java reentrant locks, so it will pass
                    // through the synchronized block there
                    synchronized (topicCallbacks) {
                        for (Map.Entry<String, MqttAndroidClientCallbackPair> entry : topicCallbacks.entrySet()) {
                            directSubscribeTopic(entry.getKey());
                        }
                    }

                    publishStatusUpdate();

                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setPersistBuffer(true);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);

                    mqttClient.setBufferOpts(disconnectedBufferOptions);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w(TAG, "Mqtt connect failed with error: " + exception.getMessage());
                    status = ConnectionStatus.DISCONNECTED;
                    publishStatusUpdate();
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Exception connecting to MQTT: " + e.getMessage());
        }
        return null;
    }

    public IMqttToken disconnectAndClose() {
        if (mqttClient != null && mqttClient.isConnected()) {
            Log.i(TAG, "Disconnecting and closing mqttClient");
            status = ConnectionStatus.DISCONNECTING;
            publishStatusUpdate();

            try {
                return mqttClient.disconnect(quiesceTimeout, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.i(TAG, "Disconnected mqttClient gracefully");
                        status = ConnectionStatus.DISCONNECTED;
                        publishStatusUpdate();

                        try {
                            mqttClient.close();
                        } catch (MqttException e) {
                            Log.e(TAG, "Exception closing client: " + e.getMessage());
                        } finally {
                            mqttClient = null;
                            status = ConnectionStatus.CLOSED;
                            publishStatusUpdate();
                        }
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "Disconnect failed for mqttClient: " + exception.getMessage());
                        if (mqttClient != null && mqttClient.isConnected()) {
                            status = ConnectionStatus.CONNECTED;
                            publishStatusUpdate();
                        }
                    }
                });
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt disconnect failed with error: " + e.getMessage());
            }
        }
        return null;
    }

    public IMqttDeliveryToken publishMessage(String topic, byte[] message) {
        try {
            return mqttClient.publish(topic, message, mqttQos, false);
        } catch (MqttException e) {
            Log.e(TAG, "Exception publishing message: " + e.getMessage());
        }
        return null;
    }

    private void completeMessage(String topic, MqttMessage msg) {
        Log.i(TAG, "Topic: " + topic + ", received: " + msg);
        try {
            mqttClient.messageArrivedComplete(msg.getId(), mqttQos);
        } catch (MqttException e) {
            Log.e(TAG, "Exception marking message arrived complete: " + e.getMessage());
        }
    }


    /* Inner classes, enums and interfaces */

    public static abstract class MqttAndroidClientCallback {
        private String topic;
        public MqttAndroidClientCallback(String topic) { this.topic = topic; }
        public String getTopic() { return this.topic; }
        abstract public void handleComms(MqttMessage message);
    }

    private static class MqttAndroidClientCallbackPair {
        private MqttAndroidClientCallback callback;
        private boolean directlySubscribed;
        MqttAndroidClientCallbackPair(MqttAndroidClientCallback cb) {
            callback = cb;
            directlySubscribed = false;
        }
    }

    public enum ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, CLOSED, UNKNOWN
    }

    public interface ConnectionListener {
        @WorkerThread
        void onConnectionStatusChange(ConnectionStatus status);
    }
}
