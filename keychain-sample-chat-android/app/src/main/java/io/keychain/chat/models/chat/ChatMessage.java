package io.keychain.chat.models.chat;

public class ChatMessage {
    public String id;

    public String chatId;

    public ChatDirection sendOrRcvd;

    public String senderId;

    public String receiverId;

    public String imageUrl;

    public String msg;

    public Long timestamp;
}
