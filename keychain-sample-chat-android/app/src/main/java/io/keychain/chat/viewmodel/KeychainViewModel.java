package io.keychain.chat.viewmodel;

import static io.keychain.common.Constants.ALL;
import static io.keychain.common.Constants.ERROR_UPDATING_PERSONA_PROFILE_TO_CHATS_DATABASE_FOR;
import static io.keychain.common.Constants.NO_ACTIVE_PERSONA;
import static io.keychain.common.Constants.PERSONA_SUCCESSFULLY_UPDATED_IN_CHATS_DATABASE_FOR;
import static io.keychain.common.Constants.RECORD_ID;
import static io.keychain.common.Constants.SOMETHING_WENT_WRONG;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import io.keychain.chat.interfaces.ChatRepository;
import io.keychain.chat.models.CreatePersonaResult;
import io.keychain.chat.models.PendingPersona;
import io.keychain.chat.models.PersonaLoginStatus;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.services.GatewayService;
import io.keychain.chat.services.database.SQLiteDBService;
import io.keychain.common.Constants;
import io.keychain.core.Contact;
import io.keychain.core.Facade;
import io.keychain.core.Persona;
import io.keychain.core.PersonaStatus;
import io.keychain.core.Refreshable;
import io.keychain.core.SecurityLevel;
import io.keychain.core.Uri;
import io.keychain.mobile.KeychainApplication;
import io.keychain.mobile.util.Utils;
import io.keychain.chat.views.TabbedActivity;

public abstract class KeychainViewModel extends AndroidViewModel implements Refreshable {
    private static final String TAG = "KeychainViewModel";
    public static final String PERSONAS_MAP_SIZE = "personasMap.size = ";
    public static final String PENDING_PERSONA_MAP_SIZET = "pendingPersonaMap.size = ";
    public static final String PLATFORM_USERS_SIZE = "platformUsers.size = ";
    public static final String USER_MAP_SIZE = "userMap.size ";
    public static final String USERS_COUNT = "users.count = ";
    public static final String TEMP_USERS_COUNT = "tempUsers.count = ";
    public static final String ERROR_LOADING_CHATS = "Error loading chats: ";
    public static final String ERROR_CREATING_RECORD_FOR_ALL_USER_CHAT = "Error creating record for ALL user chat.";
    public static final String NO_CHAT_USER_FOR_PENDING_PERSONA = "No chat user for pending persona: ";
    public static final String PERSONA_ALREADY_EXISTS = "Persona already exists";
    public static final String LAST_NAME_MUST_NOT_BE_BLANK = "Sub name must not be blank";
    public static final String FIRST_NAME_MUST_NOT_BE_BLANK = "Name must not be blank";

    protected final GatewayService gatewayService;
    protected final ChatRepository chatRepository;

    protected final MutableLiveData<Persona> persona;
    protected final MutableLiveData<List<User>> personas;
    private final MutableLiveData<PersonaLoginStatus> isPersonaMature;

    protected  final Map<String, Facade> personasMap = new TreeMap<>();

    protected final MutableLiveData<List<User>> contacts;
    protected  final Map<String, Facade> contactsMap = new TreeMap<>();

    protected Map<String, User> userMap = new TreeMap<>();
    protected Map<String, PendingPersona> pendingPersonaMap = new TreeMap<>();
    protected List<Chat> chatList = new ArrayList<>();

    protected boolean updateUsers = false;

    private ReentrantLock updatingUsersLock = new ReentrantLock();

    protected final Application application;
    public LiveData<PersonaLoginStatus> isPersonaConfirmed() {
        return isPersonaMature;
    }

    protected TabbedActivity tabbedActivity;

    public KeychainViewModel(Application application) {
        super(application);

        assert application instanceof KeychainApplication;
        this.application = application;
        this.gatewayService = ((KeychainApplication)application).getGatewayService();
        this.chatRepository = new SQLiteDBService(application);

        persona = new MutableLiveData<>();
        personas = new MutableLiveData<>(Collections.EMPTY_LIST);
        contacts = new MutableLiveData<>(Collections.EMPTY_LIST);

        Persona activePersona = gatewayService.getActivePersona();
        persona.setValue(activePersona);

        // initialize values here - if we find we don't get them fast enough in the usual refresh(), put them here
        getPersonas();
        getContacts();

        List<User> ps = getChatPersonas();
        personas.setValue(ps);
        persona.setValue(null); // set to null to force explicit #setActivePersona call; setting to gatewayService#getActivePersona() may cause non-null persona to be set on startup
        isPersonaMature = new MutableLiveData<>();
        isPersonaMature.setValue(PersonaLoginStatus.NONE);

        contacts.setValue(ps.isEmpty() ? Collections.EMPTY_LIST : getChatContacts());
    }

    public Application getApplication() {
        return application;
    }

    private List<Facade> getContacts() {
        List<Facade> contactList = gatewayService.getContacts();

        for (Facade contact : contactList) {
            try {
                // Make sure it has a valid URI. Otherwise, remove it.
                contact.getUri().toString();
            } catch (Exception e) {
                Log.w(TAG, "Contact has no URI, deleting it.", e);
                gatewayService.deleteContact((Contact) contact);
                continue;
            }

            try {
                contactsMap.put(contact.getUri().toString(), contact);
            } catch (Exception ex) {
                Log.e(TAG, Constants.ERROR_GETTING_CONTACTS, ex);
                return new ArrayList<>();
            }
        }

        return contactList;
    }

    private List<Facade> getPersonas() {
        try {
            List<Facade> personasList = gatewayService.getPersonas();

            for (Facade facade : personasList) {
                Persona persona = (Persona) facade;

                String name = persona.getName() + " " + persona.getSubName();

                if (persona.getStatus() == PersonaStatus.CONFIRMED) {
                    PendingPersona pendingPersona = pendingPersonaMap.get(name);

                    if (pendingPersona != null) {
                        // Pending persona is initially created with a null persona because
                        // persona creation takes a few seconds
                        if (pendingPersona.persona == null) {
                            pendingPersona.persona = persona;
                        }

                        saveConfirmedPersonaToDB(pendingPersona, (Persona) persona, name);
                    }

                    Uri uri = persona.getUri();
                    personasMap.put(uri.toString(), persona);
                } else {
                    PendingPersona pendingPersona = pendingPersonaMap.get(name);

                    if (pendingPersona == null) {
                        pendingPersona = createPendingPersona(persona.getName(), persona.getSubName());
                    }

                    pendingPersonaMap.put(name, pendingPersona);
                    personasMap.put(name, persona);
                }
            }

            return personasList;
        } catch (Exception ex) {
            Log.e(TAG, Constants.ERROR_GETTING_CONTACTS, ex);
            return new ArrayList<>();
        }
    }

    public Persona findPersona(String uri) {
        return personasMap.containsKey(uri) ? (Persona) personasMap.get(uri) : null;
    }

    public LiveData<Persona> getActivePersona() {
        return persona;
    }

    public LiveData<List<User>> getUserPersonas() { return personas; }
    public LiveData<List<User>> getUserContacts() {
        return contacts;
    }

    public void deleteContact(Contact contact) {
        gatewayService.deleteContact(contact);
        contacts.postValue(getChatContacts());
    }
    public void modifyContact(Contact contact, String name, String subName) {
        gatewayService.modifyContact(contact, name, subName);
        contacts.postValue(getChatContacts());
    }

    public List<User> getChatPersonas() {
        addChatUsers(personasMap);

        List<User> userList = new ArrayList<>();

        Map<String, User> usersMap = chatRepository
                .getPlatformUsers(personasMap.keySet())
                .orElse(new TreeMap<>());

        addAllChatUserIfMissing();

        for (User user : usersMap.values()) {
            if (user.getKey() != null && personasMap.containsKey(user.getKey())) {
                userList.add(user);
            }
        }

        return userList;
    }

    private void addAllChatUserIfMissing() {
        User allUser = userMap.get(ALL);

        if (allUser == null) {
            allUser = chatRepository
                    .getPlatformUserByUri(ALL)
                    .orElse(null);

            if (allUser == null) {

                String recordId = chatRepository.saveUserProfile(ALL,
                                                                 "",
                                                                 PersonaStatus.CONFIRMED.getStatusCode(),
                                                                 ALL,
                                                                 null)
                                                .orElse(null);

                if (recordId != null) {
                    allUser = chatRepository.getPlatformUser(recordId)
                            .orElse(null);
                }
            }

            if (allUser == null) {
                Log.e(TAG, ERROR_CREATING_RECORD_FOR_ALL_USER_CHAT);
                return;
            }

            userMap.put(ALL, allUser);
        }
    }

    public List<User> getChatContacts() {
        addChatUsers(contactsMap);

        return chatRepository
                .getPlatformUsers(contactsMap.keySet())
                .orElse(new TreeMap<>())
                .values()
                .stream()
                .filter(user -> {
                    String key = user.getKey();
                    return key != null && !key.isEmpty() && contactsMap.containsKey(key);
                })
                .collect(Collectors.toList());
    }

    public void addChatUsers(Map<String, Facade> facadeMap) {
        try {
            for (Facade facade : facadeMap.values()) {
                User user = chatRepository.getPlatformUser(facade.getName(), facade.getSubName())
                        .orElse(null);

                String uri;

                try {
                    uri = facade.getUri().toString();
                } catch (Exception e) {
                    // Doesnot have a uri yet
                    uri = "";
                }

                if (user == null) {
                    String recordId = chatRepository.saveUserProfile(facade.getName(),
                                                                     facade.getSubName(),
                                                                     facade.getStatus().getStatusCode(),
                                                                     uri,
                                                                     null)
                                                    .orElseThrow(() -> new Exception(Constants.ERROR_SAVING_CHAT_USER_PROFILE));

                    Log.i(TAG,"Successfully saved chat user profile. Record ID: " + recordId);
                } else if (user.status != facade.getStatus().getStatusCode() ||
                        (!uri.isEmpty() && !user.uri.equals(uri))) {

                    chatRepository.updateUserProfile(user.firstName,
                                                     user.lastName,
                                                     facade.getStatus().getStatusCode(),
                                                     uri);
                }

                if (user != null) {
                    userMap.put(user.getKey(), user);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error adding chat user to chat database: ", ex);
        }
    }

    private void updateUsersDict(List<User> tempUsers, User incoming) {
        tempUsers.add(incoming);
        userMap.remove(incoming.getName());    // If it was previously pending, remove it
        userMap.put(incoming.getKey(), incoming);
    }

    private void updateStatusOfExistingUsers(List<User> tempUsers, List<User> platformUsers) {
        for (User incoming : platformUsers) {
            User user = userMap.get(incoming.getKey());

            if (user == null) {
                updateUsersDict(tempUsers, incoming);
                continue;
            }

            // If the status of the existing user is different from the incomming
            if (user.status != incoming.status) {
                updateUsersDict(tempUsers, incoming);
            } else if (!tempUsers.isEmpty()) {
                // If at least one status updated, we need to copy the rest of the users so all
                // users will apprear in the UI. Otherwise, only the updated ones will appear
                tempUsers.add(user);
            }
        }
    }

    private PendingPersona createPendingPersona(String firstName, String lastName) {
        PendingPersona pendingPersona;
        String name = firstName + " " + lastName;
        User user = userMap.get(name);

        if (user == null) {
            user = createPendingUser(UUID.randomUUID().toString(), firstName, lastName);
            userMap.put(name, user);
        }

        pendingPersona = new PendingPersona(user.id, null, null);

        return pendingPersona;
    }

    private User createPendingUser(String recordId, String firstName, String lastName) {

        try {
            User user = new User(recordId,
                                 firstName,
                                 lastName,
                                 PersonaStatus.CREATED.getStatusCode(),
                                 null,
                                 recordId);

            userMap.put(user.getName(), user);

            return user;
        } catch (Exception ex) {
            Log.e(TAG, Constants.SOMETHING_WENT_WRONG, ex);
        }

        return null;
    }

    private void preservePendingPersonas(List<User> tempUsers, List<User> platformUsers) {
        for (User user : platformUsers) {
            try {
                if (Utils.getPersonaStatus(user.status) == PersonaStatus.CONFIRMED) {
                    pendingPersonaMap.remove(user.getKey());
                    continue;
                }

                final Persona persona = findPersona(user.uri);

                if (persona == null) {
                    continue;
                }

                // Add image later, maybe
                PendingPersona pending = new PendingPersona(user.id, persona, null);

                User pUser = createPendingUser(user.id, persona.getName(), persona.getSubName());
                pUser.photo = user.photo;

                tempUsers.add(0, pUser);

                // At first the pending user has no uri, so the name is used as the key
                // After the uri exists we use the uri as the key
                userMap.remove(pUser.getName());
                userMap.put(pUser.getKey(), pUser);

                new Thread(() -> saveToDatabase(persona));
            } catch (Exception e) {
                Log.e(TAG, "Error processing pending persona", e);
            }
        }
    }

    private void saveToDatabase(Persona persona) {
        try {
            String uri = persona.getUri().toString();
            String firstName = persona.getName();
            String lastName = persona.getSubName();
            int status = persona.getStatus().getStatusCode();

            if (chatRepository.updateUserProfile(firstName,
                                                 lastName,
                                                 status,
                                                 uri)) {

                User user = chatRepository.getPlatformUser(firstName, lastName)
                                          .orElse(null);

                if (user != null) {
                    Log.i(TAG, PERSONA_SUCCESSFULLY_UPDATED_IN_CHATS_DATABASE_FOR + firstName + " " + lastName);
                    Log.i(TAG, RECORD_ID + user.id + " - " + firstName + " " + lastName);
                    PendingPersona pUser = pendingPersonaMap.get(user.getName());

                    if (pUser == null && persona.getStatus() != PersonaStatus.CONFIRMED) {
                        createPendingPersona(persona.getName(), persona.getSubName());
                    }

                    return;
                }
            }

            Log.e(TAG, ERROR_UPDATING_PERSONA_PROFILE_TO_CHATS_DATABASE_FOR + firstName + " " + lastName);
        } catch (Exception ex) {
            Log.e(TAG, Constants.FAILED_TO_SAVE_PERSONA_TO_CHATS_DATABASE, ex);
        }
    }

    private List<User>  getUsers() {
        if (!updatingUsersLock.tryLock()) {
            return new ArrayList<>();
        }

        try {
            List<User> platformUsers = getChatPersonas();

            updatePersonasCollection(platformUsers);

            return platformUsers;
        } catch (Exception ex) {
            Log.e(TAG, SOMETHING_WENT_WRONG, ex);
        } finally {
            updatingUsersLock.unlock();
        }

        return new ArrayList<>();
    }

    public void loadChats() {
        try {
            Persona persona = this.persona.getValue();

            if (persona == null) {
                // No active persona
                return;
            }

            // URI of active persona
            final String uri = persona.getUri().toString();

            // Get persona URIs to exclude
            Set<String> excludePersonaUris =  personas.getValue()
                                                      .stream()
                                                      .filter(user -> !user.uri.equals(uri))    // Do not include active persona
                                                      .map(user -> user.uri)
                                                      .collect(Collectors.toSet());

            chatList = chatRepository.getAllChats(uri, excludePersonaUris)
                                     .orElse(new ArrayList<>());

            boolean allChatFound = false;

            for (Chat chat : chatList) {
                if (chat.participantIds.contains(ALL)) {
                    allChatFound = true;
                }
            }

            if (!allChatFound) {
                List<Chat> allChats = chatRepository.getAllChats(ALL, excludePersonaUris)
                                                    .orElse(null);

                if (allChats == null || allChats.isEmpty()) {
                    Chat allChat = new Chat(uri, ALL, "");
                    chatRepository.saveChat(allChat);
                    chatList.add(0, allChat);
                } else {
                    chatList.add(0, allChats.get(0));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, ERROR_LOADING_CHATS, e);
        }
    }


    @MainThread
    private void updatePersonasCollection(List<User> platformUsers) {
        Log.i(TAG, PERSONAS_MAP_SIZE + personasMap.size());
        Log.i(TAG, PENDING_PERSONA_MAP_SIZET +  pendingPersonaMap.size());
        Log.i(TAG, PLATFORM_USERS_SIZE + platformUsers.size());
        Log.i(TAG, USER_MAP_SIZE + userMap.size());
        Log.i(TAG, USERS_COUNT + personas.getValue().size());
        Log.i(TAG,"");

        if (platformUsers.size() > 0) {
            List<User> tempUsers = new ArrayList<>();

            preservePendingPersonas(tempUsers, platformUsers);
            updateStatusOfExistingUsers(tempUsers, platformUsers);

            Log.i(TAG, TEMP_USERS_COUNT + tempUsers.size());
            Log.i(TAG,"");
        }
    }

    // Maybe later.
    private Bitmap loadProfileImage(URL fileURL) {
        // TODO: Maybe in the future

        return null;
    }

    private void saveConfirmedPersonaToDB(PendingPersona unconfirmed, Persona persona, String name) {
        try {
            if (persona.getStatus() != PersonaStatus.CONFIRMED) {
                return;
            }

            String unconfirmedUri = unconfirmed.persona.getUri().toString();
            String uri = persona.getUri().toString();

            Log.i(TAG,"Updating persona URI: " + name);
            Log.i(TAG,"Unconfirmed URI: " + unconfirmedUri);
            Log.i(TAG,"Confirmed URI: " + uri);

            // Persona just get confirmed, now save the db with correct URI
            saveToDatabase(persona);
            pendingPersonaMap.remove(name);
            userMap.remove(name);
        } catch (Exception ex) {
            Log.e(TAG, SOMETHING_WENT_WRONG, ex);
        }
    }

    private Optional<User> getUser(Persona persona) {
        try {
            return Optional.of(userMap.get(persona.getUri().toString()));
        } catch (Exception ex) {
            Log.e(TAG, SOMETHING_WENT_WRONG, ex);
        }

        return Optional.empty();
    }

    public boolean setActivePersona(String uri) {
        Persona p = (Persona) personasMap.get(uri);

        if (p == null) {
            return false;
        }

        return setActivePersona(p);
    }

    @MainThread
    public boolean setActivePersona(Persona p) {
        if (p == null) return false;

        try {
            if (p.isMature()) {
                gatewayService.setActivePersona(p);
                persona.setValue(p);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error setting active persona: " + e.getMessage());
        }
        // set to null so observers get a callback to handle
        persona.setValue(null);
        return false;
    }

    /* NOTE: this method is on the MAIN THREAD.
        You need to either:
            1. keep it lightweight and fast
            2. put contents on a worker thread
            3. make sure you never have more than 1 view model listening to the GatewayService
        Even with #1 and #3, you may want to use a single worker thread at the view model level so UI can continue and calls to #onRefresh are not interleaved
     */
    @MainThread
    @Override
    public void onRefresh() {
        // on refresh, update live data
        // NOTE: we do *not* ask for the active persona, because Keychain always has an active persona as long as one of the personas is confirmed.
        // This makes it impossible to have a login screen where you wait for the user to select a persona before setting it active.
        // The MutableLiveData 'persona' is only updated when you setActivePersona, and users should never ask for getActivePersona on the repository

        try {
            getPersonas();
            List<User> ps = getUsers();

            personas.postValue(ps);

            if (ps.isEmpty()) {
                return;
            }

            List<Facade> contactList = getContacts();

            if (contacts.getValue() == null || contacts.getValue().size() != contactList.size()) {
                contacts.postValue(getChatContacts());
            } else {
                // lists are same length - compare contents
                Set<String> uris = new HashSet<>();
                try {
                    for (Facade c : contactList) uris.add(c.getUri().toString());
                    for (User c : contacts.getValue()) uris.remove(c.getKey());
                } catch (Exception e) {
                    Log.e(TAG, "Checking contact diffs, got exception: " + e.getMessage());
                }

                if (!uris.isEmpty()) contacts.setValue(getChatContacts());
            }

            loadChats();
        } catch (Exception ex) {
            Log.e(TAG, Constants.ERROR_REFRESHING_CONTENT, ex);
        }
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

    public void selectPersona(Persona persona) {
        // forward to KeychainViewModel, which will update 'persona' MLD if successful and do nothing otherwise
        boolean success = setActivePersona(persona);
        isPersonaMature.setValue(success ? PersonaLoginStatus.OK : PersonaLoginStatus.FAILURE);
    }

    public CreatePersonaResult createPersona(String firstName, String lastName) {
        String error;

        if (lastName.isEmpty()) {
            error = LAST_NAME_MUST_NOT_BE_BLANK;
        } else if (firstName.isEmpty()) {
            error = FIRST_NAME_MUST_NOT_BE_BLANK;
        } else {
            try {
                if (personas != null) {
                    for (Facade p : personasMap.values()) {
                        String name = p.getName();
                        String subName = p.getSubName();

                        if (p.getName().equals(firstName) && p.getSubName().equals(lastName)) {
                            return new CreatePersonaResult(false, PERSONA_ALREADY_EXISTS);
                        }
                    }
                }

                String name = firstName + " " + lastName;

                PendingPersona pendingPersona = createPendingPersona(firstName, lastName);
                pendingPersonaMap.put(name, pendingPersona);
                User user = userMap.get(name);

                if (user != null) {
                    List<User> ps = getUsers();
                    ps.add(0, user);
                    personas.setValue(ps);
                }

                new Thread(() -> {

                    Persona p = gatewayService.createPersona(firstName, lastName, SecurityLevel.MEDIUM);

                    if (p != null) {
                        Log.d(TAG, "Persona created in thread");
                    } else {
                        Log.d(TAG, "Error creating persona");
                    }
                }).start();

                return new CreatePersonaResult(true, "Persona created");
            } catch (Exception e) {
                error = "Failed to create persona. Reason: " + e.getMessage();
            }
        }

        onRefresh();
        return new CreatePersonaResult(false, error);
    }

    public void setMainActivity(TabbedActivity tabbedActivity) {
        this.tabbedActivity = tabbedActivity;
    }
}
