package io.keychain.chat.viewmodel;

import static io.keychain.common.Constants.ERROR_GETTING_MESSAGES_FOR_CHAT_ID;
import static io.keychain.common.Constants.ERROR_SAVING_CHAT_TO_DB;
import static io.keychain.common.Constants.ERROR_UPDATING_CHAT;
import static io.keychain.common.Constants.NEW_CHAT_SAVED_TO_DB_RECORD_ID;
import static io.keychain.common.Constants.NO_EXISTING_CHAT_FOR;
import static io.keychain.common.Constants.PAIR_ACK;
import static io.keychain.common.Constants.PAIR_REQUEST;
import static io.keychain.common.Constants.PAIR_RESPONSE;
import static io.keychain.common.Constants.RECEIVED;
import static io.keychain.common.Constants.RECEIVER_ID;
import static io.keychain.common.Constants.SENDER_ID;
import static io.keychain.common.Constants.SENDER_NAME;
import static io.keychain.common.Constants.SENDER_SUB_NAME;
import static io.keychain.common.Constants.SENDING;

import android.app.Application;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.keychain.chat.MqttUseCase;
import io.keychain.chat.interfaces.ChatRepository;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.ChatDirection;
import io.keychain.chat.models.chat.ChatMessage;
import io.keychain.chat.models.chat.Message;
import io.keychain.chat.models.chat.PairStatus;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.models.chat.UserSource;
import io.keychain.chat.services.channel.ChannelMessage;
import io.keychain.chat.services.database.SQLiteDBService;
import io.keychain.chat.views.contacts.ChatUser;
import io.keychain.core.Contact;
import io.keychain.core.Facade;
import io.keychain.core.Uri;
import io.keychain.mobile.util.PairHandler;
import io.keychain.mobile.util.PairHelper;
import io.keychain.mobile.util.UriUploader;
import io.keychain.mobile.util.Utils;
import io.keychain.mobile.viewmodel.KeychainViewModel;

public class TabbedViewModel extends KeychainViewModel {
    private static final String TAG = "TabbedViewModel";
    public static final String UNABLE_TO_SEND_MESSAGE_NO_RECORD_ID = "Unable to send message. No record id.";
    public static final String SENDING_CHAT_MESSAGE_TO_TOPIC = "Sending chat message to topic: ";
    public static final String UNABLE_TO_SEND_CHAT_MESSAGE = "Unable to send chat message because it was not saved to the chat database.";
    public static final String ERROR_PAIRING_USING_TRUSTED_DIRECTORY = "Error pairing using trusted directory: ";
    public static final String PAIRING_TO = "Pairing to ";
    public static final String OVER_MQTT = " over MQTT";
    public static final String ERROR_SENDING_PAIRING_REQUEST_FROM_TRUSTED_DIRECTORY = "Error sending pairing request from trusted directory.";
    public static final String TARGET_PERSONA_FOR_PAIRING_RESPONSE_NOT_FOUND_ABORTING_PAIRING_HANDSHAKE = "Target persona for pairing response not found. Aborting pairing handshake";
    public static final String ERROR_SAVING_CONTACT_TO_CHATS_DATABASE = "Error saving contact to chats database.";

    public static final String RECEIVED_CHAT_MESSAGE = "Received chat message: ";
    public static final String CHAT_MESSAGE_SAVED_TO_DATABASE = "Chat message saved to database, messageId: ";

    /* Repository */
    private final ChatRepository chatRepository;

    /* Live Data for Views to observe */
    private final MutableLiveData<List<Chat>> chatLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Message>> allMessages = new MutableLiveData<>();
    private final MutableLiveData<Message> latestMessage = new MutableLiveData<>();
    private final MutableLiveData<List<ChatUser>> chatContacts = new MutableLiveData<>();
    private final MutableLiveData<String> trustedDirectoryResult = new MutableLiveData<>();

    /* Internal state, unobservable by Views */
    private String activePersonaUri;
    private final Map<String, ChatUser> chatUserMap = new HashMap<>();

    private final PairHandler pairHandler;
    private final PairHelper pairHelper;
    private final MqttUseCase mqttUseCase;

    public TabbedViewModel(Application application, MqttUseCase useCase) {
        super(application);

        chatRepository = new SQLiteDBService(application);

        pairHandler = new PairHandler();
        pairHandler.addCallback(PAIR_REQUEST, this::handlePairMessageRequest, true);
        pairHandler.addCallback(PAIR_RESPONSE, this::handlePairMessageResponse, true);
        pairHandler.addCallback(PAIR_ACK, this::handlePairMessageAck, true);

        mqttUseCase = useCase;
        mqttUseCase.setChatCallback(this::handleChatMessage);
        mqttUseCase.setPairCallback(this::handlePairing);
        pairHelper = new PairHelper(gatewayService.getKeychainContext(), mqttUseCase.getPairingDomain());
    }

    public String getActivePersonaUri() { return activePersonaUri; }

    public LiveData<List<Chat>> getChats() { return chatLiveData; }

    public LiveData<Message> getLatestMessage() { return latestMessage; }

    public LiveData<List<Message>> getAllMessages() { return allMessages; }

    public LiveData<List<ChatUser>> getChatContacts() { return chatContacts; }

    public LiveData<String> getTrustedDirectoryResult() { return trustedDirectoryResult; }

    // Call on login: set the list of chat rooms available, set current chat to null, and set the current persona/user
    public void setChatPersona(String uri) {
        this.activePersonaUri = uri;
        UriUploader.DoUpload(new Uri(activePersonaUri), pairHelper, uploaded -> {});
        gatewayService.findPersona(uri).ifPresent(this::createGatewayChatUserIfNeeded);
        setActivePersona(uri); // TODO: needed?
        chatRepository
                .getPlatformUserByUri(uri)
                .ifPresent(user -> chatLiveData.setValue(chatRepository.getAllChats(user.id, Collections.emptySet()).orElse(Collections.emptyList())));
        setChat(null);

        // go through gateway and fill users in chat db if needed
        gatewayService.getContacts().forEach(this::createGatewayChatUserIfNeeded);
        // go through chat db users and add to map
        chatRepository.getPlatformUsers(null).ifPresent(m -> {
            m.values().forEach(u -> addChatUser(u.firstName, u.lastName, u.uri, PairStatus.fromInt(u.status), UserSource.fromInt(u.source)));
        });
        refreshChatUsers();
    }

    private boolean createChatUserIfNeeded(String name, String subName, String uri, PairStatus status, UserSource source) {
        try {
            User user = chatRepository.getPlatformUserByUri(uri).orElse(null);

            // If there is no user in the db, create
            if (user == null) {
                String recordId = chatRepository.saveUserProfile(name, subName, status.getCode(), source.getCode(), uri, null).orElse(null);
                if (recordId == null) {
                    Log.e(TAG, ERROR_SAVING_CONTACT_TO_CHATS_DATABASE);
                    return false;
                } else {
                    Log.i(TAG,"Successfully saved chat user profile. Record ID: " + recordId);
                }
            } else if (user.status != status.getCode() || !user.firstName.equals(name) || !user.lastName.equals(subName)) {
                // if there is a user, but his status, name or last name have changed, update.
                chatRepository.updateUserProfile(name, subName, status.getCode(), uri);
            }
            addChatUser(name, subName, uri, status, source);
        } catch (Exception e) {
            Log.e(TAG, "General exception: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void createGatewayChatUserIfNeeded(Facade facade) {
        try {
            createChatUserIfNeeded(facade.getName(), facade.getSubName(), facade.getUri().toString(), PairStatus.PAIRED, UserSource.GATEWAY);
        } catch (Exception e) {
            Log.e(TAG, "General exception: " + e.getMessage());
        }
    }

    private void addChatUser(String name, String subName, String uri, PairStatus status, UserSource source) {
        ChatUser user = new ChatUser(
                name,
                subName,
                uri,
                source.name().substring(0, 1),
                status == PairStatus.PAIRED,
                status == PairStatus.REQUEST_SENT,
                status == PairStatus.NONE ? () -> pair(uri, source) : null,
                status == PairStatus.RESPONSE_RECEIVED ? () -> Log.d(TAG, "Accept btn pressed, does nothing today") : null,
                status == PairStatus.RESPONSE_RECEIVED ? () -> Log.d(TAG, "Reject btn pressed, does nothing today") : null
                );
        if (!uri.equals(activePersonaUri))
            chatUserMap.put(uri, user);
    }

    public void refreshChats() {
        chatLiveData.setValue(chatRepository.getAllChats(activePersonaUri, Collections.emptySet()).orElse(Collections.emptyList()));
    }

    public void refreshChatUsers() {
        List<ChatUser> list = chatUserMap.values().stream().sorted(Comparator.comparing(a -> a.uri)).collect(Collectors.toList());
        if (Utils.IsUiThread())
            chatContacts.setValue(list);
        else
            chatContacts.postValue(list);
    }
    
    // Call on conversation click: set list of chat messages for the chat
    public Chat setChat(Chat chat) {
        if (chat != null) {
            List<Message> messages = chatRepository
                    .getAllMessages(chat)
                    .orElse(Collections.emptyList())
                    .stream().map(m -> getMessage(decrypt(m.msg), m.senderId, Utils.getDateTimeFromEpoc(m.timestamp), m.id))
                    .collect(Collectors.toList());
            allMessages.setValue(messages);
        }
        return chat;
    }

    // Call on contact click
    public Chat setChatByFacade(ChatUser user) {
        Chat chat = null;
        String senderUri = activePersonaUri;
        String recipientUri = user.uri;
        chat = chatRepository.getChat(senderUri, recipientUri).orElse(null);
        if (chat == null && user.isChattable) {
            chatRepository.saveChat(new Chat(senderUri, recipientUri, null));
            chat = chatRepository.getChat(senderUri, recipientUri).orElse(null);
        }
        return setChat(chat);
    }

    public User getUserNameForUri(String uri) {
        return chatRepository.getPlatformUserByUri(uri).orElse(null);
    }

    private boolean addContactForChat(String uri, String name, String subName, PairStatus status, UserSource source) {
        // add contact via gatewayService, update contacts
        Optional<Contact> contactOpt = gatewayService.findContact(uri);

        // if contact exists -> status = PAIRED
        if (contactOpt.isPresent()) {
            status = PairStatus.PAIRED;
        } else if (status == PairStatus.PAIRED) {
            Contact contact = gatewayService.createContact(name, subName, new Uri(uri));
            if (contact == null) {
                Log.w(TAG, "Could not create contact for Uri " + uri);
            }
        }
        return createChatUserIfNeeded(name, subName, uri, status, source);
    }

    @Override
    public void onRefresh() {
        super.onRefresh();
    }

    // Ask trusted directory for all Uris
    public void downloadTrustedDirectoryContacts() {
        try {
            int i = chatUserMap.size();
            for (Uri uri : pairHelper.getAllUri()) {
                try {
                    String uriString = uri.toString();
                    if (uriString.equals(activePersonaUri)) continue;
                    if (chatUserMap.containsKey(uriString)) continue;

                    addContactForChat(uriString, "", "", PairStatus.NONE, UserSource.TRUSTED_DIRECTORY);
                } catch (Exception e) {
                    Log.e(TAG, ERROR_PAIRING_USING_TRUSTED_DIRECTORY, e);
                }
            }
            trustedDirectoryResult.postValue("Retrieved " + (chatUserMap.size() - i) + " new contacts");
        } catch (Exception e) {
            Log.e(TAG, ERROR_SENDING_PAIRING_REQUEST_FROM_TRUSTED_DIRECTORY, e);
            trustedDirectoryResult.postValue(ERROR_SENDING_PAIRING_REQUEST_FROM_TRUSTED_DIRECTORY);
        }
        refreshChatUsers();
    }

    // Attempt to pair with the provided Uri by initiating a PairRequest
    public void pair(@NonNull String uri, UserSource source) {
        if (uri.equals(activePersonaUri)) return;
        byte[] payload = ChannelMessage.MakePairRequest(
                gatewayService.findPersona(activePersonaUri).orElse(null),
                uri,
                null).toString().getBytes(StandardCharsets.UTF_8);
        Log.d(TAG, PAIRING_TO + uri + OVER_MQTT);
        addContactForChat(uri, "", "", PairStatus.REQUEST_SENT, source);
        mqttUseCase.sendToMqttPairing(uri, payload);
        refreshChatUsers();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mqttUseCase.closeMqttChannel();
    }

    /**
     * Returns a chat for these two participants, creating a new one if none exists and paired with recipient
     */
    private Chat getChat(String senderUri, String recipientUri) throws Exception {
        Chat chat = chatRepository.getChat(senderUri, recipientUri).orElse(null);

        if (chat == null) {
            Log.i(TAG, NO_EXISTING_CHAT_FOR + recipientUri);
            // are both participants present and acked?
            Optional<User> user1 = chatRepository.getPlatformUserByUri(senderUri);
            Optional<User> user2 = chatRepository.getPlatformUserByUri(recipientUri);
            if (
                    user1.isPresent()
                    && user2.isPresent()
                    && user1.get().status == PairStatus.PAIRED.getCode()
                    && user2.get().status == PairStatus.PAIRED.getCode()
            ) {
                Log.i(TAG, NO_EXISTING_CHAT_FOR + recipientUri);
                chat = new Chat(senderUri, recipientUri, "");
                String recordId = chatRepository.saveChat(chat).orElseThrow(() -> new Exception(ERROR_SAVING_CHAT_TO_DB));

                Log.i(TAG, NEW_CHAT_SAVED_TO_DB_RECORD_ID + recordId + ", " + getOtherUserInChat(chat.participantIds).orElse(null));
            } else {
                Log.i(TAG, "Chat not created because both users not yet paired");
            }
        }

        return chat;
    }

    private void updateChat(Chat chat) throws Exception {
        if (!chatRepository.updateChat(chat.id, chat.lastMsg)) {
            throw new Exception(ERROR_UPDATING_CHAT);
        }
    }

    /**
     * Broadcast a message over MQTT.
     * The message is unmodified by this method, so make sure to encrypt prior to calling it if
     * encryption is desired.
     */
    private void sendMessage(String receiverUri, ChatMessage message) {
        Log.i(TAG, "Sending message to receiver");

        if (message == null) {
            Log.e(TAG, UNABLE_TO_SEND_CHAT_MESSAGE);
            return;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        message.msg = Base64.encodeToString(message.msg.getBytes(), Base64.NO_WRAP);
        String json = gson.toJson(message);

        // Send message to recipient(s)
        Log.i(TAG, SENDING_CHAT_MESSAGE_TO_TOPIC + receiverUri);
        Log.i(TAG, json);
        mqttUseCase.sendToMqttChat(receiverUri, json.getBytes());
    }

    // TODO: private
    public String decrypt(String msg) {
        if (msg == null || msg.trim().isEmpty())
            return "";

        return gatewayService.decryptThenVerify(msg);
    }

    public boolean isMe(String uri) {
        return activePersonaUri != null && activePersonaUri.equals(uri);
    }

    /**
     * Take a pair request from another party and create and send response with own data.
     * This method does not add the other party as contact.
     */
    private PairHandler.PairResult handlePairMessageRequest(JSONObject jsonObject) {
        try {
            String contactId = jsonObject.getString(SENDER_ID);
            String name = jsonObject.getString(SENDER_NAME);
            String subName = jsonObject.getString(SENDER_SUB_NAME);
            Log.i(TAG, RECEIVED + PAIR_REQUEST);
            JSONObject resp = ChannelMessage.MakePairResponse(gatewayService.findPersona(activePersonaUri).orElse(null), contactId);

            // Will create contact after we receive an Ack
            if (resp != null) {
                Log.i(TAG, SENDING + PAIR_RESPONSE);
                Log.i(TAG, resp.toString());
                mqttUseCase.sendToMqttPairing(contactId, resp.toString().getBytes(StandardCharsets.UTF_8));

                addContactForChat(contactId, name, subName, PairStatus.RESPONSE_RECEIVED, UserSource.MQTT);
                onRefresh(); // TODO: remove if not needed
            }
            return new PairHandler.PairResult(contactId, null);
        } catch (Exception e) {
            return new PairHandler.PairResult("-", e.getMessage());
        }
    }

    /**
     * Take a pair response from another party and create and send an ack.
     * This method will add the other party as a contact.
     */
    private PairHandler.PairResult handlePairMessageResponse(JSONObject jsonObject) {
        try {
            String contactId = jsonObject.getString(SENDER_ID);
            String contactName = jsonObject.getString(SENDER_NAME);
            String contactSubName = jsonObject.getString(SENDER_SUB_NAME);
            Log.i(TAG, RECEIVED + PAIR_RESPONSE);
            Log.i(TAG, jsonObject.toString());

            // check if we are the intended recipient and leave otherwise
            String targetPersonaId = jsonObject.getString(RECEIVER_ID);

            if (!activePersonaUri.equals(targetPersonaId)) {
                Log.w(TAG, TARGET_PERSONA_FOR_PAIRING_RESPONSE_NOT_FOUND_ABORTING_PAIRING_HANDSHAKE);
                return new PairHandler.PairResult("-", "Aborting pairing: ack received but not for us");
            }

            boolean c = addContactForChat(contactId, contactName, contactSubName, PairStatus.PAIRED, UserSource.MQTT);
            onRefresh();
            PairHandler.PairResult result = new PairHandler.PairResult(contactId, c ? null : "Failed to add contact for chat");

            // Send Ack so other party can add me as a contact
            JSONObject ack = ChannelMessage.MakePairAck(gatewayService.findPersona(activePersonaUri).orElse(null), contactId);

            // Will create contact after we receive an Ack
            if (ack != null) {
                Log.i(TAG, SENDING + PAIR_ACK);
                Log.i(TAG, ack.toString());
                mqttUseCase.sendToMqttPairing(contactId, ack.toString().getBytes(StandardCharsets.UTF_8));
            }
            return result;
        } catch (Exception e) {
            return new PairHandler.PairResult("-", e.getMessage());
        }
    }

    /**
     * Take a pair ack from another party.
     * This method will add the other party as a contact.
     */
    private PairHandler.PairResult handlePairMessageAck(JSONObject jsonObject) {
        try {
            String contactId = jsonObject.getString(SENDER_ID);
            String contactName = jsonObject.getString(SENDER_NAME);
            String contactSubName = jsonObject.getString(SENDER_SUB_NAME);
            Log.i(TAG, RECEIVED + PAIR_ACK);
            Log.i(TAG, jsonObject.toString());

            // check if we are the intended recipient and leave otherwise
            String targetPersonaId = jsonObject.getString(RECEIVER_ID);

            if (!activePersonaUri.equals(targetPersonaId)) {
                Log.w(TAG, TARGET_PERSONA_FOR_PAIRING_RESPONSE_NOT_FOUND_ABORTING_PAIRING_HANDSHAKE);
                return new PairHandler.PairResult("-", "Aborting pairing: ack received but not for us");
            }
            boolean c = addContactForChat(contactId, contactName, contactSubName, PairStatus.PAIRED, UserSource.MQTT);
            return new PairHandler.PairResult(contactId, c ? null : "Failed to add contact for chat");
        } catch (Exception e) {
            return new PairHandler.PairResult("-", e.getMessage());
        }
    }

    // Channel-agnostic handler of a pair message.
    private void handlePairing(byte[] message) {
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(new String(message));
            PairHandler.PairResult result = pairHandler.handlePairMessage(jsonObject);
            refreshChatUsers();
            if (result.isSuccess()) {
                Log.i(TAG, "Pair handled successfully");
            } else {
                Log.w(TAG, "Pair message handling did not succeed, found error: " + result.getError());
            }
        } catch (JSONException e) {
            Log.w(TAG, "Pair message handling did not succeed, found error: " + e.getMessage());
        }
    }

    private void handleChatMessage(byte[] message) {
        try {
            String json = new String(message);
            Log.i(TAG, RECEIVED_CHAT_MESSAGE + json);

            // Convert from JSON to ChatMessage
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            ChatMessage chatMessage = gson.fromJson(json, ChatMessage.class);

            // Did we already process this message?
            if (chatRepository.getMessage(chatMessage.id).isPresent() || activePersonaUri.equals(chatMessage.senderId)) {
                return;
            }

            String urlDecoded = new String(Base64.decode(chatMessage.msg, Base64.NO_WRAP));
            chatMessage.msg = urlDecoded;
            String senderUri = chatMessage.senderId;
            String receiverId = chatMessage.receiverId;
            assert Objects.equals(receiverId, activePersonaUri); // TODO: remove this after test

            handleReceivedMessage(urlDecoded, senderUri, receiverId);
        } catch (Exception e) {
            Log.e(TAG, "Error in MQTT handling: " + e.getMessage());
        }
    }

    public void openMqttChannel() {
        mqttUseCase.openMqttChannel(activePersonaUri);
    }

    // helper
    private Message getMessage(String plaintext, String senderUri, LocalDateTime dateTime, String id) {
        User author;
        Date date;
        try {
            User user = chatRepository
                    .getPlatformUserByUri(senderUri)
                    .orElseThrow(() -> new Exception("Repository does not contain a user with URI = '" + senderUri + "'"));

            // Deep copy
            Gson gson = new Gson();
            author = gson.fromJson(gson.toJson(user), User.class);
            if (isMe(senderUri)) {
                author.id = "0";
            }

            date = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
            return new Message(plaintext, author, date, id);
        } catch (Exception e) {
            Log.e(TAG, "Error adding message to UI: ", e);
            return null;
        }
    }

    private void addNewMessage(String plaintext, String senderUri, LocalDateTime dateTime, String id) {
        Message message = getMessage(plaintext, senderUri, dateTime, id);
        if (message != null) {
            if (message.text == null || message.text.trim().isEmpty()) {
                return;
            }

            if (Utils.IsUiThread())
                latestMessage.setValue(message);
            else
                latestMessage.postValue(message);
        }
    }

    // encrypt message -- used for things we type
    private String encryptMessageForOtherParticipant(String plaintext, List<String> participants) throws Exception {
        // encrypt for other participant
        final Optional<String> selectedUser = getOtherUserInChat(participants);
        if (selectedUser.isPresent()) {
            final Optional<Contact> recipient = gatewayService.findContact(selectedUser.get());
            if (recipient.isPresent())
                return gatewayService.signThenEncrypt(List.of(recipient.get()), plaintext);
        }
        throw new Exception("Could not find contact with uri = " + selectedUser);
    }

    // store message
    private ChatMessage storeMessage(String ciphertext, String senderUri, String receiverUri, ChatDirection direction) throws Exception {
        try {
            Chat chat = getChat(senderUri, receiverUri);

            chat.lastMsg = ciphertext;
            updateChat(chat);

            // Save message to chats database
            String recordId = chatRepository.saveMessage(UUID.randomUUID().toString(),
                            senderUri,
                            ciphertext,
                            direction,
                            chat)
                    .orElseThrow(() -> new Exception(UNABLE_TO_SEND_MESSAGE_NO_RECORD_ID));
            ChatMessage savedMessage = chatRepository.getMessage(recordId).orElseThrow(() -> new Exception(ERROR_GETTING_MESSAGES_FOR_CHAT_ID + recordId));
            Log.d(TAG, CHAT_MESSAGE_SAVED_TO_DATABASE + recordId);
            return savedMessage;
        } catch (Exception e) {
            throw new Exception("Exception storing message: " + e.getMessage(), e.getCause());
        }
    }

    // from our typing
    public void handleSubmittedMessage(String plaintext, List<String> participants) throws Exception {
        String ciphertext = encryptMessageForOtherParticipant(plaintext, participants);
        String otherUri = getOtherUserInChat(participants).orElse(null);
        ChatMessage message = storeMessage(ciphertext, activePersonaUri, otherUri, ChatDirection.send);
        addNewMessage(plaintext, activePersonaUri, Utils.getDateTimeFromEpoc(message.timestamp), message.id);
        sendMessage(otherUri, message);
    }

    private void handleReceivedMessage(String ciphertext, String senderUri, String receiverUri) throws Exception {
        ChatMessage message = storeMessage(ciphertext, senderUri, receiverUri, ChatDirection.receive);
        String plaintext = decrypt(ciphertext);
        addNewMessage(plaintext, activePersonaUri, Utils.getDateTimeFromEpoc(message.timestamp), message.id);
    }

    private Optional<String> getOtherUserInChat(List<String> participants) {
        return participants.stream().filter(s -> !s.equals(activePersonaUri)).findFirst();
    }
}
