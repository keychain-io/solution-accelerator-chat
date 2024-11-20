package io.keychain.mobile.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.keychain.mobile.services.GatewayService;
import io.keychain.core.Contact;
import io.keychain.core.Facade;
import io.keychain.core.Persona;
import io.keychain.core.Refreshable;
import io.keychain.mobile.KeychainApplication;
import io.keychain.mobile.util.Utils;

public abstract class KeychainViewModel extends AndroidViewModel implements Refreshable {
    private static final String TAG = "KeychainViewModel";
    public static final String PERSONA_ALREADY_EXISTS = "Persona already exists";
    public static final String LAST_NAME_MUST_NOT_BE_BLANK = "Sub name must not be blank";
    public static final String FIRST_NAME_MUST_NOT_BE_BLANK = "Name must not be blank";

    protected final GatewayService gatewayService;

    /* Livedata you can listen to for basic Keychain events */
    private final MutableLiveData<Persona> persona;
    private final MutableLiveData<List<Facade>> personas;
    private final MutableLiveData<List<Facade>> contacts;

    /* State */
    protected final Application application;

    public KeychainViewModel(Application application) {
        super(application);

        this.application = application;
        this.gatewayService = ((KeychainApplication)application).getGatewayService();

        persona = new MutableLiveData<>();
        personas = new MutableLiveData<>(Collections.emptyList());
        contacts = new MutableLiveData<>(Collections.emptyList());
        persona.setValue(null); // set to null to force explicit #setActivePersona call; setting to gatewayService#getActivePersona() may cause non-null persona to be set on startup

    }

    @NonNull
    public Application getApplication() { return application; }

    public LiveData<Persona> getActivePersona() { return persona; }
    public LiveData<List<Facade>> getPersonas() { return personas; }
    public LiveData<List<Facade>> getContacts() { return contacts; }

    public void deleteContact(Contact contact) {
        gatewayService.deleteContact(contact);
        contacts.setValue(gatewayService.getContacts());
    }
    public void modifyContact(Contact contact, String name, String subName) {
        gatewayService.modifyContact(contact, name, subName);
        contacts.setValue(gatewayService.getContacts());
    }

    public void refreshPersonas() {
        List<Facade> personasList = gatewayService.getPersonas();
        if (Utils.IsUiThread())
            personas.setValue(personasList);
        else
            personas.postValue(personasList);
    }
    public void refreshContacts() {
        if (gatewayService.getActivePersona() != null) {
            List<Facade> contactsList = gatewayService.getContacts();
            if (Utils.IsUiThread())
                contacts.setValue(contactsList);
            else
                contacts.postValue(contactsList);
        }
    }

    @MainThread
    public void setActivePersona(String uri) {
        Optional<Persona> optionalPersona = gatewayService.findPersona(uri);
        optionalPersona.filter(this::setActivePersona);
    }

    @MainThread
    public boolean setActivePersona(Persona p) {
        if (p == null) return false;

        try {
            if (p.isMature()) {
                gatewayService.setActivePersona(p);
                persona.setValue(p);
                contacts.setValue(gatewayService.getContacts());
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error setting active persona: " + e.getMessage());
        }
        // set to null so observers get a callback to handle in the failure case
        persona.setValue(null);
        contacts.setValue(Collections.emptyList());
        return false;
    }

    public boolean contactExists(String uri) {
        return gatewayService.findContact(uri).isPresent();
    }


    /* NOTE: this method is on the MAIN THREAD.  Keep it fast and lightweight */
    @MainThread
    @Override
    public void onRefresh() {
        refreshPersonas();
        refreshContacts();
    }

    @CallSuper
    public void startListeners() {
        Log.d(TAG, "Starting listeners");
        gatewayService.registerRefreshListener(this);
    }

    @CallSuper
    public void stopListeners() {
        Log.d(TAG, "Stopping listeners");
        gatewayService.unregisterRefreshListener(this);
    }

    @CallSuper
    @Override
    protected void onCleared() {
        Log.d(TAG, "ViewModel cleared");
        // Just in case, unregister here too
        gatewayService.unregisterRefreshListener(this);
    }
}
