package io.keychain.chat;

import static io.keychain.common.Constants.ALL;

import android.util.Log;

import java.io.IOException;
import java.util.function.Consumer;

import io.keychain.chat.services.MqttService;
import io.keychain.chat.services.channel.Channel;
import io.keychain.chat.services.channel.ChannelStatus;
import io.keychain.chat.services.channel.MqttChannel;

public class MqttUseCase {
    private static final String TAG = "PairUseCase";

    private final MqttService service;
    private Channel mqttChannel;
    private final String pairingChannel;
    private final String chatChannel;
    private boolean mqttConnected;
    private Consumer<byte[]> pairCallback;
    private Consumer<byte[]> chatCallback;
    private final String pairingDomain;

    public MqttUseCase(MqttService service, String domain, String pairingChannel, String chatChannel) {
        this.service = service;
        this.pairingChannel = pairingChannel;
        this.chatChannel = chatChannel;
        this.pairingDomain = KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_TRUSTED_DIRECTORY_PREFIX) + domain;
    }

    public String getPairingDomain() {
        return pairingDomain;
    }

    public void setPairCallback(Consumer<byte[]> callback) {
        pairCallback = callback;
    }
    public void setChatCallback(Consumer<byte[]> callback) {
        chatCallback = callback;
    }

    public void closeMqttChannel() {
        if (mqttChannel != null) {
            try {
                mqttChannel.close();
            } catch (IOException e) {
                Log.w(TAG, "Exception closing MQTT channel: " + e.getMessage());
            }
        }
        mqttChannel = null;
    }

    public void sendToMqttPairing(String uri, byte[] message) {
        sendToMqtt(pairingChannel + uri, message);
    }
    public void sendToMqttChat(String uri, byte[] message) {
        sendToMqtt(chatChannel + uri, message);
    }

    private void sendToMqtt(String topic, byte[] message) {
        if (mqttChannel != null) {
            mqttChannel.send(topic, message);
        }
    }

    protected void close(Channel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                Log.w(TAG, "Exception closing channel: " + e.getMessage());
            }
        }
    }

    protected void updateMqttStatus(ChannelStatus status) {
        mqttConnected = status == ChannelStatus.CONNECTED;
    }

    protected boolean isMqttConnected() {
        return mqttChannel != null && mqttConnected;
    }

    public void openMqttChannel(String activePersonaUri) {
        if (mqttChannel != null || activePersonaUri == null) return;

        String[] topics = new String[] {
                pairingChannel + activePersonaUri,
                chatChannel + activePersonaUri,
                chatChannel + ALL
        };

        mqttChannel = new MqttChannel(service, topics) {
            @Override
            public void onReceive(String source, byte[] message) {
                if (source.startsWith(pairingChannel)) {
                    pairCallback.accept(message);
                } else if (source.startsWith(chatChannel)) {
                    chatCallback.accept(message);
                }
            }

            @Override
            public void onStatusChange(ChannelStatus status) {
                updateMqttStatus(status);
            }
        };
    }
}
