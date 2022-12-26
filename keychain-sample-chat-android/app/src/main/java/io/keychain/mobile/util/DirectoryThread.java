package io.keychain.mobile.util;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.keychain.core.Contact;
import io.keychain.core.Gateway;
import io.keychain.core.Persona;
import io.keychain.core.Uri;

/**
 * Utility class that can be put on a thread to continuously run in the background and synchronize contacts and
 * own persona to/from the trusted directories passed to it.
 *
 * On construction pass all needed PairHelper instances (each PairHelper represents 1 domain)
 */
public class DirectoryThread implements Runnable {
    private static final String TAG = "DirectoryThread";
    private volatile boolean shouldStop;
    private final Gateway gateway;

    static ReentrantLock mutex = new ReentrantLock();

    private final List<PairHelper> pairHelpers;

    public DirectoryThread(Gateway gateway, PairHelper... pairHelpers) {
        this.gateway = gateway;
        this.pairHelpers = new ArrayList<>(pairHelpers.length);
        this.pairHelpers.addAll(Arrays.asList(pairHelpers));
    }

    private void doContactSync(PairHelper pairHelper) {
        final String DOMAIN = pairHelper.getDomain();
        Log.i(TAG, DOMAIN + " In doContactSync");
        try {
            Log.i(TAG, DOMAIN + " Contacts starts @ " + this.gateway.getContacts().size());
            List<Uri> serverUris = pairHelper.getAllUri();
            Persona persona = this.gateway.getActivePersona();
            List<Contact> contacts = this.gateway.getContacts();
            List<Persona> personas = this.gateway.getPersonas();
            Uri personaUri = persona.getUri();

            boolean selfFound = false;

            List<Uri> myUris = new ArrayList<>(contacts.size() + personas.size());
            for (Contact c : contacts) myUris.add(c.getUri());
            for (Persona p : personas) myUris.add(p.getUri());

            // for each server URI, check if it is contact/self, and add if not; at the end, if self wasn't found, upload
            for (Uri uri : serverUris) {
                boolean shouldAdd = true;
                for (Uri myUri : myUris) {
                    if (uri.toString().equals(personaUri.toString())) {
                        selfFound = true;
                        shouldAdd = false;
                        break;
                    }
                    else if (uri.toString().equals(myUri.toString())) {
                        shouldAdd = false;
                        break;
                    }
                }
                if (shouldAdd) {
                    // add contact
                    Log.i(TAG, " Adding contact for URI: " + uri.toString());
                    this.gateway.createContact(uri.toString().substring(0, 16), pairHelper.getDomain(), new Uri(uri.toString()));
                    Log.i(TAG, " Contacts now @ " + this.gateway.getContacts().size());
                }
            }
            if (!selfFound) {
                // add self
                Log.i(TAG, DOMAIN + " Adding persona to Trusted Directory: " + personaUri.toString());
                pairHelper.uploadUri(personaUri);
            }
        } catch (Exception e) {
            Log.e(TAG, DOMAIN + "  Unhandled exception bubbled from doContactSync: " + e.getMessage());
        }
    }

    public void run() {
        this.shouldStop = false;
        Log.i(TAG, " Directory thread Running");
        while (!this.shouldStop) {
            for (final PairHelper pairHelper : this.pairHelpers) {
                // lock access to doContactSync
                mutex.lock();
                doContactSync(pairHelper);
                mutex.unlock();
            }

            try {
                Thread.sleep(17000L); // 17s wait
            } catch (Exception e) {
                Log.e(TAG, "Exception sleeping in directory thread: " + e.getMessage());
            }
        }
        Log.d(TAG, "Exiting thread");
    }

    public void onStop() {
        this.shouldStop = true;
    }
}
