package io.keychain.chat;

import android.util.Log;

import io.keychain.mobile.KeychainApplication;
import io.keychain.chat.services.MqttService;

public class KeychainApp extends KeychainApplication {
    private static final String TAG = "KeychainApp";

    // Properties
    public static final String PROPERTY_MQTT_HOST = "mqtt.host";
    public static final String PROPERTY_MQTT_PORT = "mqtt.port";
    public static final String PROPERTY_MQTT_CHANNEL_PAIRING = "mqtt.channel.pairing";
    public static final String PROPERTY_MQTT_CHANNEL_CHATS = "mqtt.channel.transfer";
    public static final String PROPERTY_TRUSTED_DIRECTORY_PREFIX = "trusted.directory.domain.prefix";
    public static final String PROPERTY_TRUSTED_DIRECTORY_HOST = "trusted.directory.host";
    public static final String PROPERTY_TRUSTED_DIRECTORY_PORT = "trusted.directory.port";

    public static final int DENOMINATION = 1;
    private MqttService mqttService;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();

        mqttService = new MqttService(getApplicationContext().getDir("mqtt", MODE_PRIVATE).getAbsolutePath(), getApplicationProperty(KeychainApp.PROPERTY_MQTT_HOST));
    }

    public MqttService getMqttRepository() { return mqttService; }

    @Override
    protected void onForeground() {
        super.onForeground();
    }

    @Override
    protected void onBackground() {
        super.onBackground();
    }
}

