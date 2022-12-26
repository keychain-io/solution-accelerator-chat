package io.keychain.chat.viewmodel;

import static io.keychain.common.Constants.ALL;
import static io.keychain.common.Constants.ERROR_SAVING_CHAT_TO_DB;
import static io.keychain.common.Constants.ERROR_UPDATING_CHAT;
import static io.keychain.common.Constants.MSG_TYPE;
import static io.keychain.common.Constants.NEW_CHAT_SAVED_TO_DB_RECORD_ID;
import static io.keychain.common.Constants.NO_ACTIVE_PERSONA;
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
import static io.keychain.common.Constants.UNEXPECTED_EXCEPTION;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.ChatDirection;
import io.keychain.chat.models.chat.ChatMessage;
import io.keychain.chat.models.chat.Message;
import io.keychain.chat.models.chat.User;
import io.keychain.common.Constants;
import io.keychain.core.Contact;
import io.keychain.core.Persona;
import io.keychain.core.Uri;
import io.keychain.chat.KeychainApp;
import io.keychain.chat.services.channel.Channel;
import io.keychain.chat.services.channel.ChannelStatus;
import io.keychain.mobile.threading.TaskRunner;
import io.keychain.chat.services.channel.ChannelMessage;
import io.keychain.mobile.util.PairHelper;
import io.keychain.mobile.util.UriUploader;

public abstract class ChatViewModel extends KeychainViewModel {
    private static final String TAG = "ChatViewModel";
    public static final String UNABLE_TO_SEND_MESSAGE_NO_RECORED_ID = "Unable to send message. No recored id.";
    public static final String SENDING_CHAT_MESSAGE_TO_TOPIC = "Sending chat message to topic: ";
    public static final String UNABLE_TO_SEND_CHAT_MESSAGE = "Unable to send chat message because it was not saved to the chat database.";
    public static final String ERROR_SENDING_MESSAGE = "Error sending message: ";
    public static final String ERROR_SETTING_CHAT_RECIPIENT = "Error setting chat recipient: ";
    public static final String ERROR_LOADING_MESSAGES = "Error loading messages: ";
    public static final String ERROR_PAIRING_USING_TRUSTED_DIRECTORY = "Error pairing using trusted directory: ";
    public static final String SENDING_PAIR_REQUEST_TO = "Sending pair request to: ";
    public static final String PAIRING_TO = "Pairing to ";
    public static final String OVER_MQTT = " over MQTT";
    public static final String ERROR_SENDING_PAIRING_REQUEST_FROM_TRUSTED_DIRECTORY = "Error sending pairing request from trusted directory.";
    public static final String TARGET_PERSONA_FOR_PAIRING_RESPONSE_NOT_FOUND_ABORTING_PAIRING_HANDSHAKE = "Target persona for pairing response not found. Aborting pairing handshake";
    public static final String ADDING_CONTACT_URI = "Adding contact (URI): ";
    public static final String CONTACT_CREATION_FAILED = "Contact creation failed";
    public static final String UNHANDLED_MESSAGE_IN_PAIRING_HANDLER = "Unhandled message in pairing handler: ";
    public static final String ERROR_SAVING_CONTACT_TO_CHATS_DATABASE = "Error saving contact to chats database.";

    private Gson gson = new Gson();

    protected String uploadDomain;

    protected Channel mqttChannel;

    private Contact filterContact;

    public final String pairingChannel;
    public final String chatChannel;
    protected final TaskRunner taskRunner;

    private boolean mqttConnected;

    private final SharedPreferences sharedPreferences;

    private boolean sendToAllContacts;
    private User chatRecipient;
    private Chat selectedChat;

    private List<ChatMessage> messages;

    public MessagesListAdapter<Message> messageAdapter;

    public ChatViewModel(Application application) {
        super(application);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application);

        pairingChannel = KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_MQTT_CHANNEL_PAIRING);
        chatChannel = KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_MQTT_CHANNEL_CHATS);
        taskRunner = new TaskRunner();
    }

    public abstract void openMqttChannel();

    public void closeMqttChannel() {
        if (mqttChannel != null) {
            try {
                mqttChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mqttChannel = null;
    }

    public LiveData<Persona> getActivePersona() {
        return persona;
    }

    public void sendToMqtt(String topic, byte[] message) {
        if (mqttChannel != null) {
            mqttChannel.send(topic, message);
        }
    }

    protected void close(Channel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                Log.w(TAG, "Exception closing channel: " + e.getMessage());
            }
        }
    }

    @Override
    public void onRefresh() {
        super.onRefresh();

        // upload self to trusted directory; no callback necessary for this VM
        if (persona.getValue() != null) {
            try {
                String pairingDomain = KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_TRUSTED_DIRECTORY_PREFIX) + uploadDomain;

                /* Upload self to approver-merchant so the Approver can find us */
                PairHelper pairHelper = new PairHelper(
                        KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_TRUSTED_DIRECTORY_HOST),
                        Integer.parseInt(KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_TRUSTED_DIRECTORY_PORT)),
                        pairingDomain);

                UriUploader.DoUpload(persona.getValue().getUri(), pairHelper, uploaded -> {});
            } catch (Exception e) {
                Log.e(TAG, "Error getting persona URI to upload: " + e.getMessage());
            }
        }
    }

    public boolean contactExists(String uri) {
        return gatewayService.findContact(uri) != null;
    }

    public Contact findContact(String uri) {
        return gatewayService.findContact(uri);
    }

    @Override
    public void deleteContact(Contact contact) {
        super.deleteContact(contact);
    }

    public void pairUsingTrustedDirectory() {
        try {
            String pairingDomain = KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_TRUSTED_DIRECTORY_PREFIX) + uploadDomain;

            PairHelper pairHelper = new PairHelper(
                    KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_TRUSTED_DIRECTORY_HOST),
                    Integer.parseInt(KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_TRUSTED_DIRECTORY_PORT)),
                    pairingDomain);

            String myUri = getActivePersona().getValue().getUri().toString();

            for (Uri uri : pairHelper.getAllUri()) {
                try {
                    String uriString = uri.toString();

                    if (myUri.equals(uriString)) {
                        continue;
                    }

                    Log.i(TAG, SENDING_PAIR_REQUEST_TO + uri);
                    pair(uriString);

                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    Log.e(TAG, ERROR_PAIRING_USING_TRUSTED_DIRECTORY, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, ERROR_SENDING_PAIRING_REQUEST_FROM_TRUSTED_DIRECTORY, e);
        }
    }

    public void pair(String uri, String overrideSubName) {
        byte[] payload = ChannelMessage.MakePairRequest(persona.getValue(), uri, overrideSubName).toString().getBytes(StandardCharsets.UTF_8);
        Log.d(TAG, PAIRING_TO + uri + OVER_MQTT);
        sendToMqtt(pairingChannel + uri, payload);
    }

    public void pair(String uri) {
        pair(uri, null);
    }

    // Channel-agnostic handler of a pair message - returns true if contact exists now, false otherwise
    protected Contact pairingHandler(JSONObject jsonObject) {
        Contact rc = null;
        try {
            String mtype = jsonObject.getString(MSG_TYPE);
            // pairResponse - make sure we have this contact (we should)
            boolean isRequest = mtype.equals(PAIR_REQUEST);
            boolean isResponse = mtype.equals(PAIR_RESPONSE);
            boolean isAck = mtype.equals(PAIR_ACK);

            if (isRequest || isResponse || isAck) {
                String contactId = jsonObject.getString(SENDER_ID);
                String contactName = jsonObject.getString(SENDER_NAME);
                StringBuilder contactSubName = new StringBuilder(jsonObject.getString(SENDER_SUB_NAME));

                if (isRequest) {
                    Log.i(TAG, RECEIVED + PAIR_REQUEST);
                    JSONObject resp = ChannelMessage.MakePairResponse(persona.getValue(), jsonObject);

                    // Will create contact after we receive an Ack
                    if (resp != null) {
                        Log.i(TAG, SENDING + PAIR_RESPONSE);
                        Log.i(TAG, resp.toString());
                        sendToMqtt(pairingChannel + contactId, resp.toString().getBytes(StandardCharsets.UTF_8));
                    }
                } else if (isResponse || isAck) {
                    Log.i(TAG, RECEIVED + (isResponse ? PAIR_RESPONSE : PAIR_ACK));
                    Log.i(TAG, jsonObject.toString());

                    String activePersonaUri = getActivePersona().getValue() == null ? "" : getActivePersona().getValue().getUri().toString();
                    String targetPersonaId = jsonObject.getString(RECEIVER_ID);

                    if (!activePersonaUri.equals(targetPersonaId)) {
                        Log.e(TAG, TARGET_PERSONA_FOR_PAIRING_RESPONSE_NOT_FOUND_ABORTING_PAIRING_HANDSHAKE);
                        return null;
                    }

                    // Search for the contact and add if necessary, returning false if failure
                    rc = (Contact) contactsMap.get(contactId);

                    if (rc == null) {
                        Log.i(TAG, ADDING_CONTACT_URI + contactId);
                        rc = gatewayService.createContact(contactName, contactSubName.toString(), new Uri(contactId));


                        if (rc == null) {
                            Log.w(TAG, CONTACT_CREATION_FAILED);
                            return null;
                        }
                    }

                    if (!chatRepository.getPlatformUserByUri(contactId).isPresent()) {
                        String recordId = chatRepository.saveUserProfile(rc.getName(),
                                        rc.getSubName(),
                                        rc.getStatus().getStatusCode(),
                                        rc.getUri().toString(),
                                        null)
                                .orElse(null);

                        if (recordId == null) {
                            Log.e(TAG, ERROR_SAVING_CONTACT_TO_CHATS_DATABASE);
                            return rc;
                        }
                    }

                    if (isResponse) {
                        // Send Ack so other party can add me as a contact
                        JSONObject ack = ChannelMessage.MakePairAck(persona.getValue(), jsonObject);

                        // Will create contact after we receive an Ack
                        if (ack != null) {
                            Log.i(TAG, SENDING + PAIR_ACK);
                            Log.i(TAG, ack.toString());
                            sendToMqtt(pairingChannel + contactId, ack.toString().getBytes(StandardCharsets.UTF_8));
                        }
                    }

                    onRefresh();
                }
            } else {
                Log.d(TAG, UNHANDLED_MESSAGE_IN_PAIRING_HANDLER + mtype);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, UNEXPECTED_EXCEPTION + e.getMessage());
            return null;
        }
        return rc;
    }

    protected void updateMqttStatus(ChannelStatus status) {
        mqttConnected = status == ChannelStatus.CONNECTED;
    }

    protected boolean isMqttConnected() {
        return mqttChannel != null && mqttConnected;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        closeMqttChannel();
    }

    public User getChatRecipient() {
        return chatRecipient;
    }

    public void setChatRecipient(User user) throws Exception {
        Persona persona = getActivePersona().getValue();

        if (persona == null) {
            Log.e(TAG, NO_ACTIVE_PERSONA);
            return;
        }

        chatRecipient = user;

        Chat chat = null;

        if (chatRecipient != null) {
            String senderUir = persona.getUri().toString();
            chat = getChat(senderUir, chatRecipient.uri);
        }

        setSelectedChat(chat);
    }

    @NonNull
    public Chat getChat(String senderUir, String recipientUri) throws Exception {
        Chat chat = chatRepository.getChat(senderUir, recipientUri)
                                  .orElse(null);

        if (chat == null) {
            Log.i(TAG, NO_EXISTING_CHAT_FOR + recipientUri);
            chat = new Chat(senderUir, recipientUri, "");

            saveChat(chat);
        }

        return chat;
    }

    public void saveChat(Chat chat) throws Exception {
        String recordId = chatRepository.saveChat(chat)
                                        .orElseThrow(() -> new Exception(ERROR_SAVING_CHAT_TO_DB));

        Log.i(TAG, NEW_CHAT_SAVED_TO_DB_RECORD_ID + recordId + ", " + chatRecipient.getName());
    }

    public void updateChat(Chat chat) throws Exception {
        if (!chatRepository.updateChat(chat.id, chat.lastMsg)) {
            throw new Exception(ERROR_UPDATING_CHAT);
        }
    }

    public Chat getSelectedChat() {
        return selectedChat;
    }

    public void setSelectedChat(Chat chat) {
        selectedChat = chat;

        if (chat == null) {
            messages = null;
            return;
        }

        setSendToAllContacts(chat.participantIds.contains(Constants.ALL));
        loadMessages();
    }

    public boolean isSendToAllContacts() {
        return sendToAllContacts;
    }

    public void setSendToAllContacts(boolean sendToAllContacts) {
        this.sendToAllContacts = sendToAllContacts;
    }

    public void loadMessages() {
        try {
            if (selectedChat == null) {
                Log.e(TAG, ERROR_LOADING_MESSAGES);
                return;
            }

            messages = isSendToAllContacts()
                    ? chatRepository.getAllMessages(ALL)
                                    .orElse(new ArrayList<>())
                    : chatRepository.getAllMessages(selectedChat)
                                    .orElse(new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, ERROR_LOADING_MESSAGES, e);
        }
    }

    public List<ChatMessage> getMessages() {
        if (messages == null) {
            loadMessages();
        }

        return messages;
    }

    public boolean sendMessage(String senderUri, String receiverUri, String msg) {
        try {
            Log.i(TAG, "Sending message to receiver");

            // Check that we have a selected chat
            if (selectedChat == null) {
                Log.w(TAG, "No selected chat.");
                return false;
            }

            List<Contact> contacts = new ArrayList<>();

            if (!receiverUri.equals(Constants.ALL)) {
                // Sending to one contact
                Contact contact = findContact(receiverUri);

                if (contact == null) {
                    Log.e(TAG, "Unable to send message. Contact not found for recipient: " + receiverUri);
                    return false;
                }

                contacts.add(contact);
            } else {
                // Send to all contacts
                contacts = gatewayService.getContacts()
                                         .stream()
                                         .map(facade -> (Contact) facade)
                                         .collect(Collectors.toList());
            }

            String encryptedMessage = signThenEncrypt(contacts, msg);

            // Save message to chats database
            String recordId = chatRepository.saveMessage(null,
                                                         senderUri,
                                                         encryptedMessage,
                                                         ChatDirection.send,
                                                         selectedChat)
                                            .orElseThrow(() -> new Exception(UNABLE_TO_SEND_MESSAGE_NO_RECORED_ID));

            ChatMessage message = chatRepository.getMessage(recordId)
                                                .orElse(null);

            if (message == null) {
                Log.e(TAG, UNABLE_TO_SEND_CHAT_MESSAGE);
                return false;
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            message.msg = Base64.encodeToString(message.msg.getBytes(), Base64.NO_WRAP);
            String json = gson.toJson(message);

            // Send message to recipient(s)
            String topic = chatChannel + receiverUri;
            Log.i(TAG, SENDING_CHAT_MESSAGE_TO_TOPIC + topic);
            Log.i(TAG, json);

            sendToMqtt(topic, json.getBytes());
        } catch (Exception e) {
            Log.e(TAG, ERROR_SENDING_MESSAGE, e);
            return false;
        }

        return true;
    }

    private String signThenEncrypt(List<Contact> contacts, String msg) {
        return gatewayService.signThenEncrypt(contacts, msg);
    }

    public String decrypt(String msg) {
        if (msg == null || msg.trim().isEmpty())
            return "";

        return  gatewayService.decryptThenVerify(msg);
    }

    public void addToMessageList(String msg, String senderUri, LocalDateTime dateTime) {
        tabbedActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (msg == null || msg.trim().isEmpty()) {
                    return;
                }

                try {
                    User user = userMap.get(senderUri);

                    // Deep copy
                    User author = gson.fromJson(gson.toJson(user), User.class);

                    if (isMe(senderUri)) {
                        author.id = "0";
                    }

                    Date date = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
                    messageAdapter.addToStart(new Message(msg, author, date), true);
                } catch (Exception e) {
                    Log.e(TAG, "Error adding message to UI: ", e);
                }
            }
        });
    }

    public boolean isMe(String uri) throws Exception {
        Persona activePersona = getActivePersona().getValue();
        return activePersona.getUri().toString().equals(uri);
    }

    public Map<String, User> getUserMap() {
        return userMap;
    }

    public List<Chat> getChatList() {
        return chatList;
    }
}
