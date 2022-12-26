package io.keychain.chat.services.channel;

public enum ChannelStatus {
    // OFF = closed or unopened; DISCONNECTED = opened successfully; CONNECTED = can transmit
    OFF, DISCONNECTED, CONNECTED, ERROR
}
