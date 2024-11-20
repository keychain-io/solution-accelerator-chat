package io.keychain.chat.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Optional;

import io.keychain.chat.models.CreatePersonaResult;
import io.keychain.chat.models.PersonaLoginStatus;
import io.keychain.core.Persona;
import io.keychain.core.SecurityLevel;
import io.keychain.exceptions.BadJniInput;
import io.keychain.exceptions.NoUri;
import io.keychain.mobile.viewmodel.KeychainViewModel;

public class PersonaViewModel extends KeychainViewModel {
    private static final String TAG = "PersonaViewModel";

    private final MutableLiveData<PersonaLoginStatus> personaLoginStatus = new MutableLiveData<>();

    public PersonaViewModel(Application application) {
        super(application);
        personaLoginStatus.setValue(new PersonaLoginStatus(null, PersonaLoginStatus.PersonaLoginState.NONE));
    }

    public LiveData<PersonaLoginStatus> isPersonaConfirmed() {
        return personaLoginStatus;
    }

    public void selectPersona(Persona persona) {
        // forward to KeychainViewModel, which will update 'persona' MLD if successful and do nothing otherwise
        boolean success = setActivePersona(persona);
        String uri = null;
        PersonaLoginStatus.PersonaLoginState state = success ? PersonaLoginStatus.PersonaLoginState.OK : PersonaLoginStatus.PersonaLoginState.FAILURE;

        try {
            uri = persona.getUri().toString();
        } catch (BadJniInput | NoUri e) {
            Log.e(TAG, "Error getting uri in selectPersona: " + e.getMessage());
            state = PersonaLoginStatus.PersonaLoginState.FAILURE;
        }
        personaLoginStatus.setValue(new PersonaLoginStatus(uri, state));
    }

    public CreatePersonaResult createPersona(String firstName, String lastName) {
        String error;

        if (lastName.isEmpty()) {
            error = LAST_NAME_MUST_NOT_BE_BLANK;
        } else if (firstName.isEmpty()) {
            error = FIRST_NAME_MUST_NOT_BE_BLANK;
        } else {
            // does the name exist already in personas?
            final Optional<Persona> samePersona = gatewayService.findPersona(firstName, lastName);
            if (samePersona.isPresent()) {
                error = PERSONA_ALREADY_EXISTS;
            } else {
                new Thread(() -> {
                    Persona p = gatewayService.createPersona(firstName, lastName, SecurityLevel.MEDIUM);
                    if (p != null) {
                        Log.d(TAG, "Persona created in thread");
                    } else {
                        Log.d(TAG, "Error creating persona");
                    }
                    refreshPersonas();
                }).start();
                return new CreatePersonaResult(true, "Persona created");
            }
        }

        return new CreatePersonaResult(false, error);
    }
}
