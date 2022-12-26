package io.keychain.chat.models;

public class CreatePersonaResult {
    final public boolean created;
    final public String message;

    public CreatePersonaResult(boolean c, String m) { created = c; message = m; }
}
