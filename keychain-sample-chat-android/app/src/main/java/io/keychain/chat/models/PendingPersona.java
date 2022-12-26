package io.keychain.chat.models;

import android.graphics.Bitmap;

import io.keychain.core.Persona;

public class PendingPersona {
    public Persona persona;
    public Bitmap image;        // For future use
    public String recordId;

    public PendingPersona(String id, Persona persona, Bitmap image) {
        this.recordId = id;
        this.persona = persona;
        this.image = image;
    }
}
