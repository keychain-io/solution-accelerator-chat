package io.keychain.chat.models.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Chat {
    public Chat() {
        this.id = UUID.randomUUID().toString().toUpperCase();
        this.participantIds = new ArrayList<>();
        this.lastMsg = null;
        this.timestamp = LocalDateTime.now();
    }

    public Chat(String senderUri, String recipientUri, String lastMsg) {
        this.id = UUID.randomUUID().toString().toUpperCase();
        this.participantIds = new ArrayList<>();
        this.participantIds.add(senderUri);
        this.participantIds.add(recipientUri);
        this.lastMsg = lastMsg;
        this.timestamp = LocalDateTime.now();
    }

    public String id;

    public List<String> participantIds;

    public String lastMsg;

    public LocalDateTime timestamp;
}
