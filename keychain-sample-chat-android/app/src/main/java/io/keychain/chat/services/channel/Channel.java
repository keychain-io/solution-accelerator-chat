package io.keychain.chat.services.channel;

import androidx.annotation.AnyThread;

import java.io.Closeable;

/**
 * Channel is a simple interface intended to be used by Keychain communications layers.
 * It provides a way to send a primitive byte array payload to a destination and
 * receive primitive byte array payloads back.
 *
 * Implementations will generally define #send concretely because it is an active method called *by*
 * the application, so the contents won't vary by application.
 * However, #onReceive will often be abstract because it is a passive method which leads *into* the
 * app, and therefore what it does inside will be application-specific.
 *
 * Implementations should perform any necessary initialization, startup, connection etc for the transport
 * in the constructor so that no implementation-specific methods (e.g. init, open, setSomething, ...) are required.
 *
 * The #onReceive method is marked with @AnyThread so that implementations are forced to treat
 * it in a threadsafe manner.  This has a performance cost and delay to updates.
 */
public interface Channel extends Closeable {
    void send(String destination, byte[] message);

    @AnyThread
    void onReceive(String source, byte[] message);

    @AnyThread
    void onStatusChange(ChannelStatus status);
}
