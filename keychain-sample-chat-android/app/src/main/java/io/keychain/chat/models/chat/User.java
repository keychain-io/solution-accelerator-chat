package io.keychain.chat.models.chat;

import com.stfalcon.chatkit.commons.models.IUser;

public class User implements IUser {
    public String id;

    public String firstName;

    public String lastName;

    public int status;

    public int source;    // For later, maybe

    public String photo;

    public String uri;

    public User(String id, String firstName, String lastName, int status, int source, String photo, String uri) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.source = source;
        this.photo = photo;
        this.uri = uri;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    @Override
    public String getAvatar() {
        return null;
    }

    public UserSource getSource() {
        return UserSource.fromInt(source);
    }
}
