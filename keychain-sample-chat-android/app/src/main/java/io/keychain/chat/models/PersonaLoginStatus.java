package io.keychain.chat.models;

public class PersonaLoginStatus {
    public String personaUri;
    public PersonaLoginState personaLoginState;

    public enum PersonaLoginState {
        NONE, FAILURE, OK
    }
    public PersonaLoginStatus(String uri, PersonaLoginState state) {
        this.personaUri = uri;
        this.personaLoginState = state;
    }
}
