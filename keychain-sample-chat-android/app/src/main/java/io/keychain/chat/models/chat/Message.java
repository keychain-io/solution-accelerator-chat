package io.keychain.chat.models.chat;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.commons.models.IUser;

import java.util.Date;

public class Message implements IMessage {
    public IUser author;
    public Date createdAt;
    public String text;
    public String id;

    public Message(String text, IUser author, Date created, String id)
    {
        this.author = author;
        this.text = text;
        this.createdAt = created;
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public IUser getUser() {
        return author;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }
}