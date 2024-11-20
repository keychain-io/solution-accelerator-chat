package io.keychain.chat.models.chat;

import java.util.Arrays;

public enum PairStatus {
    NONE(0),
    REQUEST_SENT(1),
    RESPONSE_RECEIVED(2),
    PAIRED(3);

    private final int code;

    PairStatus(int code) { this.code = code; }

    public int getCode() { return code; }

    public static PairStatus fromInt(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst().orElse(null);
    }
}
