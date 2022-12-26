package io.keychain.chat.models.chat;

import com.stfalcon.chatkit.commons.models.IUser;

import java.util.UUID;

import io.keychain.core.PersonaStatus;

public class User implements IUser {
    public String id = UUID.randomUUID().toString().toUpperCase();

    public String firstName;

    public String lastName;

    public int status;

    public String photo;    // For later, maybe

    public String uri;

    public User(String id, String firstName, String lastName, int status, String photoPath, String uri) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.photo = photoPath;
        this.uri = uri;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        String name = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
        return name;
    }

    @Override
    public String getAvatar() {
        return photo;
    }

    public String getKey() {
        if ((status != PersonaStatus.CONFIRMED.getStatusCode()) || (uri == null || uri.isEmpty() || uri.length() < 100)) {
            return getName();
        }

        return uri;
    }
}
