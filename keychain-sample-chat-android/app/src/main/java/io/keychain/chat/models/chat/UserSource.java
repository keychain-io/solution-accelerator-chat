package io.keychain.chat.models.chat;

import java.util.Arrays;

public enum UserSource {
    GATEWAY(0),
    TRUSTED_DIRECTORY(1),
    QR_CODE(2),
    MQTT(3),
    DEFAULT(4);

    private final int code;

    UserSource(int code) {
        this.code = code;
    }

    public int getCode() { return code; }

    public static UserSource fromInt(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst().orElse(null);
    }
}
