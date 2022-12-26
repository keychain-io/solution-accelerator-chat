package io.keychain.chat.services.channel;

import android.os.ConditionVariable;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import io.keychain.chat.services.MqttService;

/**
 * MqttChannel is an abstract implementation of the Channel interface designed around
 * MqttService, which in turn uses MqttAsyncClient.
 *
 * Users of MqttChannel must implement #onReceive and #onStatusChange to handle business logic.
 */
public abstract class MqttChannel implements Channel {
    private static final String TAG = "MqttChannel";
    private final MqttService mqtt;
    private final Set<String> topics;
    private int retryTimeout = 0;
    private static final int MAX_RETRY_TIMEOUT = 20000;

    // Message buffer
    private final Deque<MqttChannelMessage> messageQueue;
    private static final int MAX_QUEUE_SIZE = 20;

    /*
        publishThread sleeps (waits) on 2 conditions - queueCondition and connectionCondition
        When the queue has something in it, queueCondition is opened.
        When the connection status is CONNECTED, connectedCondition is opened, and now it can drain the queue, then close queueCondition.
     */
    private final Thread publishThread;
    private final ConditionVariable queueCondition = new ConditionVariable(false);
    private final ConditionVariable connectedCondition = new ConditionVariable(false);
    private volatile boolean shouldStop;
    private static final int CONDITION_TIMEOUT = 5000; // have a timeout for conditions so we don't leave the thread running forever
    // Internal MqttService ConnectionStatus listener
    // It just toggles the condition variable based on connected status
    private final MqttService.ConnectionListener statusListener;

    class MqttChannelMessage {
        final String destination;
        final byte [] message;
        MqttChannelMessage(String d, byte [] m) { this.destination = d; this.message = m; }
    }

    public MqttChannel(MqttService mqttService, String ...subscriptions) {
        mqtt = mqttService;
        topics = new HashSet<>(subscriptions.length);
        messageQueue = new ArrayDeque<>(MAX_QUEUE_SIZE);

        for (String subscription : subscriptions) {
            topics.add(subscription);
            mqtt.subscribeTopic(subscription, new MqttService.MqttAndroidClientCallback(subscription) {
                @Override
                public void handleComms(MqttMessage message) {
                    onReceive(subscription, message.getPayload());
                }
            });
        }

        statusListener = status -> {
            Log.d(TAG, "MqttChannel status changed to " + status);
            if (status == MqttService.ConnectionStatus.CONNECTED) {
                connectedCondition.open();
            } else {
                connectedCondition.close();
            }

            // initializeMqtt takes a persistence path and a host
            switch(status) {
                case CLOSED:
                    mqtt.initializeMqtt();
                    onStatusChange(ChannelStatus.OFF);
                    break;
                case DISCONNECTED:
                    onStatusChange(ChannelStatus.DISCONNECTED);
                    if (retryTimeout > 0) {
                        try {
                            Thread.sleep(retryTimeout);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    incrementRetryTimeout();
                    mqtt.connectMqtt(); // NOTE: mqtt may already be connecting for you, this will just return
                    break;
                case CONNECTING:
                    onStatusChange(ChannelStatus.DISCONNECTED);
                    break;
                case CONNECTED:
                    retryTimeout = 0;
                    onStatusChange(ChannelStatus.CONNECTED);
                    break;
                case DISCONNECTING:
                    onStatusChange(ChannelStatus.DISCONNECTED);
                    break;
                default:
                    Log.w(TAG, "Unhandled status");
            }
        };

        // add it as a listener *and* call on status with the result (which is current status)
        statusListener.onConnectionStatusChange(mqtt.addStatusListener(statusListener));

        publishThread = new Thread(() -> {
            while (!shouldStop) {
                if (queueCondition.block(CONDITION_TIMEOUT)) {
                    if (connectedCondition.block(CONDITION_TIMEOUT)) {
                        synchronized (messageQueue) {
                            // drain the queue
                            while (!messageQueue.isEmpty()) {
                                MqttChannelMessage msg = messageQueue.remove();
                                mqtt.publishMessage(msg.destination, msg.message); // Note: we assume publish goes through
                            }
                            queueCondition.close();
                        }
                    }
                }
            }
        });

        // always start the thread
        publishThread.start();
    }

    private void incrementRetryTimeout() {
        // 0, 2, 4, 8, 16, 20s
        if (retryTimeout == 0) retryTimeout = 2000;
        else retryTimeout = Math.min(MAX_RETRY_TIMEOUT, retryTimeout * 2);
    }

    @Override
    public void send(String destination, byte[] message) {
        MqttChannelMessage msg = new MqttChannelMessage(destination, message);
        synchronized(messageQueue) {
            if (!messageQueue.offer(msg)) {
                Log.d(TAG, "Queue full; removing oldest element to push new one");
                messageQueue.remove();
                messageQueue.offer(msg);
            }
            queueCondition.open();
        }
    }

    @Override
    public void close() throws IOException {
        // remove the listener first, because otherwise we might kick off a reconnect when MQTT status changes
        mqtt.removeStatusListener(statusListener);
        for (String topic : topics) {
            mqtt.unsubscribeTopic(topic);
        }
        // stop the publish thread
        shouldStop = true;
        mqtt.disconnectAndClose();
    }
}
