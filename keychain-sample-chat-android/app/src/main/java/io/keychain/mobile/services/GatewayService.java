package io.keychain.mobile.services;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.keychain.core.Contact;
import io.keychain.core.Facade;
import io.keychain.core.Gateway;
import io.keychain.core.Persona;
import io.keychain.core.Refreshable;
import io.keychain.core.SecurityLevel;
import io.keychain.core.Uri;
import io.keychain.exceptions.BadJniInput;
import io.keychain.exceptions.NoUri;

public class GatewayService {
    private static final String TAG = "GatewayService";
    public static final String ERROR_DECRYPTING_MESSAGE = "Error decrypting message.";

    // Load the Keychain native libraries
    static {
        // Load c++_shared first
        // https://developer.android.com/ndk/guides/cpp-support#shared_runtimes
        // "Old versions of Android had bugs...
        // In particular, if your app targets a version of Android earlier than Android 4.3
        // (Android API level 18), and you use libc++_shared.so, you must load the shared
        // library before any other library that depends on it."
        System.loadLibrary("c++_shared");
        System.loadLibrary("keychain-jni");
        System.loadLibrary("keychain");
    }

    private final io.keychain.core.Context keychainContext;
    public io.keychain.core.Context getKeychainContext() { return keychainContext; }
    private final Gateway gateway;
    private final MonitorService monitorService;

    public GatewayService(android.content.Context context) throws Exception {
        keychainContext = Gateway.initializeDb(context);

        if (keychainContext.isNull()) {
            throw new Exception("DB initialization failed");
        }

        gateway = new Gateway(keychainContext, context);
        // ViewModels will add themselves to the service and remove themselves from the service
        monitorService = new MonitorService(gateway);
        gateway.seed();
    }

    public void registerRefreshListener(Refreshable refreshable) {
        monitorService.addListener(refreshable);
    }
    public void unregisterRefreshListener(Refreshable refreshable) {
        monitorService.removeListener(refreshable);
    }

    public List<Facade> getPersonas() {
        return gateway.getPersonas()
                      .stream()
                      .map(x -> { return (Facade) x;})
                      .collect(Collectors.toList());
    }

    public void setActivePersona(Persona persona) {
        gateway.setActivePersona(persona);
    }

    private Optional<? extends Facade> findFacadeByUri(List<? extends Facade> facades, @NonNull String uri) {
        return facades.stream().filter(f -> {
            try {
                return f.getUri().toString().equals(uri);
            } catch (BadJniInput | NoUri e) {
                return false;
            }
        }).findFirst();
    }
    private Optional<? extends Facade> findFacadeByNames(List<? extends Facade> facades, @NonNull String name, @NonNull String subName) {
        return facades.stream().filter(f -> {
            try {
                return f.getName().equals(name) && f.getSubName().equals(subName);
            } catch (BadJniInput e) {
                return false;
            }
        }).findFirst();
    }

    public Optional<Persona> findPersona(@NonNull String uri) {
        return findFacadeByUri(gateway.getPersonas(), uri).map(f -> (Persona)f);
    }
    public Optional<Persona> findPersona(@NonNull String name, @NonNull String subName) {
        return findFacadeByNames(gateway.getPersonas(), name, subName).map(f -> (Persona)f);
    }

    public Optional<Contact> findContact(@NonNull String uri) {
        return findFacadeByUri(gateway.getContacts(), uri).map(f -> (Contact) f);
    }
    public Optional<Contact> findContact(@NonNull String name, @NonNull String subName) {
        return findFacadeByNames(gateway.getContacts(), name, subName).map(f -> (Contact) f);
    }

    public Persona getActivePersona() {
        try {
            return gateway.getActivePersona();
        } catch (Exception e) {
            // Don't log this -- Log.e(TAG, "Error getting active Persona: " + e.getMessage());
        }
        return null;
    }

    public Persona createPersona(String firstName, String lastName, SecurityLevel level) {
        return gateway.createPersona(firstName, lastName, level);
    }

    public Contact createContact(String firstName, String lastName, Uri uri) {
        try {
            return gateway.createContact(firstName, lastName, uri);
        } catch (Exception e) {
            Log.e(TAG, "Error creating contact: " + e.getMessage());
        }
        return null;
    }

    public List<Facade> getContacts() {
        return gateway.getContacts()
                      .stream()
                      .map(x -> { return (Facade) x;})
                      .collect(Collectors.toList());
    }

    public void deleteContact(Contact contact) {
        gateway.deleteContact(contact);
    }

    public void modifyContact(Contact contact, String firstName, String lastName) {
        gateway.renameContact(contact, firstName, lastName);
    }

    public void stopMonitor() { if (monitorService != null) monitorService.stop(); }
    public void startMonitor() { if (monitorService != null) monitorService.start(); }

    public String signThenEncrypt(List<Contact> contacts, String msg) {
        return gateway.signThenEncrypt(new ArrayList<>(contacts), msg.getBytes());
    }

    public String decryptThenVerify(String cipherText) {
        try {
            Log.i(TAG, "decryptThenVerify: " + cipherText);
            return new String(gateway.decryptThenVerify(cipherText));
        } catch (Exception e) {
            Log.e(TAG, ERROR_DECRYPTING_MESSAGE, e);
            return "";
        }
    }

    private static class MonitorService implements Refreshable {
        private static final String TAG = "MonitorService";
        private final Gateway gateway;
        private final Set<Refreshable> listeners;

        public MonitorService(Gateway gateway) {
            gateway.setRefreshable(this);
            this.gateway = gateway;
            listeners = new HashSet<>();
        }

        public void addListener(Refreshable listener) {
            synchronized (listeners) {
                listeners.add(listener);
            }
        }

        public void removeListener(Refreshable listener) {
            synchronized (listeners) {
                if (listeners.isEmpty()) return;

                listeners.remove(listener);
            }
        }

        private void start() {
            Log.d(TAG, "Starting");
            gateway.onStart();
            gateway.onResume();
        }
        private void stop() {
            Log.d(TAG, "Stopping");
            new Thread(() -> {
                gateway.onPause();
                try {
                    gateway.onStop();
                } catch (Exception e) {
                    Log.e(TAG, "Error shutting down monitor: " + e.getMessage());
                }
            }).start();
        }

        @MainThread
        @Override
        public void onRefresh() {
            Log.d(TAG, "Refreshing");
            synchronized (listeners) {
                for (Refreshable r : listeners) {
                    r.onRefresh();
                }
            }
        }
    }
}
