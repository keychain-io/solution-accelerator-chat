package io.keychain.chat.models.chat;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.commons.models.IUser;

import java.util.Date;


public class Message implements IMessage {

    /*...*/
    private String id;
    public IUser author;
    public Date createdAt;
    public String text;

    public Message(String text, IUser author, Date created)
    {
        this.author = author;
        this.text = text;
        this.createdAt = created;
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