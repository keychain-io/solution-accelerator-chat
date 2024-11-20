package io.keychain.chat.views.contacts;

public class ChatUser {
    public interface ChatUserCallback {
        void callback();
    }

    public final String name;
    public final String subName;
    public final String uri;
    public final String source;
    public final boolean isChattable;
    public final boolean isPendingResponse;
    public final ChatUserCallback onRequest;
    public final ChatUserCallback onAccept;
    public final ChatUserCallback onReject;

    public ChatUser(String n, String sn, String u, String s, boolean isC, boolean isP, ChatUserCallback req, ChatUserCallback acc, ChatUserCallback rej) {
        name = n;
        subName = sn;
        uri = u;
        source = s;
        isChattable = isC;
        isPendingResponse = isP;
        onRequest = req;
        onReject = rej;
        onAccept = acc;
    }
}
