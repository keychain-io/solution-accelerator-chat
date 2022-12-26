package io.keychain.chat.models.chat;

public enum ChatDirection {
    // Keep as lower case. iPhone chat app will fail to decode the message if uppercase.
    send("send"),
    receive("receive");

    private String direction;

    ChatDirection(String direction) {
        this.direction = direction;
    }

    public String getDirection() {
        return direction;
    }
}
