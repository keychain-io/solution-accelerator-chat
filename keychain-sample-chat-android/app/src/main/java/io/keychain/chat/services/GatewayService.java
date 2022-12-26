package io.keychain.chat.services;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.keychain.core.Asset;
import io.keychain.core.Contact;
import io.keychain.core.Facade;
import io.keychain.core.Gateway;
import io.keychain.core.LedgerResult;
import io.keychain.core.LedgerTransaction;
import io.keychain.core.Persona;
import io.keychain.core.Refreshable;
import io.keychain.core.SecurityLevel;
import io.keychain.core.Uri;

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

    private io.keychain.core.Context keychainContext;

    private final Gateway gateway;
    private final MonitorService monitorService;

    public GatewayService(android.content.Context context) {
        keychainContext = Gateway.initializeDb(context);

        if (!keychainContext.isNull()) {
            Log.i(TAG, "DB init OK");
        } else {
            Log.e(TAG, "DB init FAILED");
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

    public Contact findContact(@NonNull String uri) {
        List<Contact> contacts = gateway.getContacts();
        try {
            for (Contact contact : contacts) {
                if (uri.equals(contact.getUri().toString())) {
                    return contact;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking contacts for existence: " + e.getMessage());
        }
        return null;
    }

    public Contact findContact(@NonNull String fn, @NonNull String ln, String uri) {
        List<Contact> contacts = gateway.getContacts();
        try {
            for (Contact contact : contacts) {
                if (fn.equals(contact.getName()) && ln.equals(contact.getSubName())) {
                    return contact;
                }
                if (uri != null && uri.equals(contact.getUri().toString())) {
                    return contact;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking contacts for existence: " + e.getMessage());
        }
        return null;
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

    public Asset createAsset(String symbol) {
        try {
            return gateway.createAsset(symbol);
        } catch (Exception e) {
            Log.e(TAG, "Error creating asset: " + e.getMessage());
        }
        return null;
    }

    public List<Facade> getContacts() {
        return gateway.getContacts()
                      .stream()
                      .map(x -> { return (Facade) x;})
                      .collect(Collectors.toList());
    }

    public List<Asset> getGatewayAssets() {
        try {
            return gateway.getAssets();
        } catch (Exception e) {
            Log.e(TAG, "Error getting Assets: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public long getLedgerBalance(Asset asset) {
        if (asset == null) return 0;
        try {
            return gateway.getLedgerBalance(asset);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Ledger Balance: " + e.getMessage());
        }
        return 0;
    }

    public LedgerResult transferAsset(Contact receiver, Facade approver, Asset asset, long amount, String reason, boolean isIssuance) {
        LedgerResult result = null;
        try {
            result = gateway.transferAsset(receiver, approver, asset, amount, reason, isIssuance);
        } catch (Exception e) {
            Log.e(TAG, "Error transferring asset: " + e.getMessage());
        }
        return result;
    }

    public LedgerResult updateLedger(String txnStr, Contact approver) {
        LedgerResult result = null;
        try {
            result = gateway.updateLedger(txnStr, approver);
        } catch (Exception e) {
            Log.e(TAG, "Error updating ledger: " + e.getMessage());
        }
        return result;
    }

    // get own ledger transactions
    public List<LedgerTransaction> getLedgerTransactions() {
        List<LedgerTransaction> results = new ArrayList<>();
        try {
            Persona p = getActivePersona();
            if (p == null) return results;
            String pUri = p.getUri().toString();

            List<LedgerTransaction> txns = gateway.getLedgerTransactions();
            for (LedgerTransaction txn : txns) {
                if (txn.getSenderUrl().equals(pUri) || txn.getReceiverUrl().equals(pUri)) {
                    results.add(txn);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting ledger transactions");
        }
        return results;
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
        return gateway.signThenEncrypt((ArrayList<Contact>) contacts, msg.getBytes());
    }

    public String decryptThenVerify(String cipherText) {
        try {
            Log.i(TAG, "decryptThenVerify: " + cipherText);
            String decrypted = new String(gateway.decryptThenVerify(cipherText));
            return decrypted;
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
