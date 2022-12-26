package io.keychain.chat.viewmodel;

import static io.keychain.chat.services.channel.ChannelStatus.CONNECTED;
import static io.keychain.common.Constants.ALL;
import static io.keychain.common.Constants.ERROR_GETTING_MESSAGES_FOR_CHAT_ID;
import static io.keychain.common.Constants.SENDER_ID;

import android.app.Application;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import io.keychain.chat.KeychainApp;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.ChatDirection;
import io.keychain.chat.models.chat.ChatMessage;
import io.keychain.chat.services.MqttService;
import io.keychain.chat.services.channel.ChannelStatus;
import io.keychain.chat.services.channel.MqttChannel;
import io.keychain.core.Persona;
import io.keychain.mobile.util.Utils;

public class TabbedViewModel extends ChatViewModel {
    private static final String TAG = "TabbedViewModel";
    public static final String RECEIVED_CHAT_MESSAGE = "Received chat message: ";
    public static final String CHAT_MESSAGE_SAVED_TO_DATABASE = "Chat message saved to database, messageId: ";
    public static final String ERROR_CONVERTING_MESSAGE_TO_JSON = "Error converting message to JSON: ";

    private final MutableLiveData<String> notificationMessage;
    private boolean localIsMqttConnected = false;

    public TabbedViewModel(Application application) {
        super(application);
        notificationMessage = new MutableLiveData<>();
        uploadDomain = "keychain-chat";
    }

    @Override
    public void openMqttChannel() {
        if (mqttChannel != null) return;
        MqttService mqttService = ((KeychainApp) application).getMqttRepository();

        String uri = null;

        try {
            if (getActivePersona().getValue() != null) {
                uri = getActivePersona().getValue().getUri().toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting active persona URI in setMqttChannel: " + e.getMessage());
        }

        if (uri == null) return;

        String[] topics = new String[] {
                pairingChannel + uri,
                chatChannel + uri,
                chatChannel + ALL
        };

        mqttChannel = new MqttChannel(mqttService, topics) {
            @Override
            public void onReceive(String source, byte[] message) {

                if (source.startsWith(pairingChannel)) {
                    JSONObject jsonObject;
                    String senderId = null;

                    try {
                        jsonObject = new JSONObject(new String(message));
                        if (jsonObject.has(SENDER_ID)) {
                            senderId = jsonObject.getString(SENDER_ID);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, ERROR_CONVERTING_MESSAGE_TO_JSON + e.getMessage());
                        return;
                    }

                    pairingHandler(jsonObject);
                } else if (source.startsWith(chatChannel)) {
                    try {
                        String json = new String(message);
                        Log.i(TAG, RECEIVED_CHAT_MESSAGE + json);

                        // Convert from JSON to ChatMessage
                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                        ChatMessage chatMessage = gson.fromJson(json, ChatMessage.class);

                        // Did we already processed this message?
                        if (messageAlreadyReceived(chatMessage.id)) {
                            return;
                        }

                        Persona activePersona = persona.getValue();

                        if (activePersona.getUri().toString().equals(chatMessage.senderId)) {
                            // I am the sender, ignore
                            return;
                        }

                        String urlDecoded = new String(Base64.decode(chatMessage.msg, Base64.NO_WRAP));
                        chatMessage.msg = urlDecoded;

                        String senderUri = chatMessage.senderId;
                        String receiverId = chatMessage.receiverId;

                        Chat chat = getChat(senderUri, receiverId);
                        chat.lastMsg = chatMessage.msg;
                        updateChat(chat);

                        // Save message to chats database
                        String recordId = chatRepository.saveMessage(chatMessage.id,
                                                                     senderUri,
                                                                     chatMessage.msg,
                                                                     ChatDirection.receive,
                                                                     chat)
                                                        .orElseThrow(() -> new Exception(UNABLE_TO_SEND_MESSAGE_NO_RECORED_ID));

                        ChatMessage savedMessage = chatRepository.getMessage(recordId)
                                        .orElseThrow(() -> new Exception(ERROR_GETTING_MESSAGES_FOR_CHAT_ID + recordId));

                        Log.d(TAG, CHAT_MESSAGE_SAVED_TO_DATABASE + recordId);

                        displayMessage(urlDecoded,
                                       senderUri,
                                       Utils.getDateTimeFromEpoc(savedMessage.timestamp),
                                       Utils.isAllChat(chatMessage));
                    } catch (Exception e) {
                        Toast.makeText(application.getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onStatusChange(ChannelStatus status) {
                updateMqttStatus(status);
                localIsMqttConnected = status == CONNECTED;
            }
        };
    }

    private boolean messageAlreadyReceived(String chatMessageId) {
        return chatRepository
                .getMessage(chatMessageId)
                .isPresent();
    }

    public void displayMessage(String msg, String senderUri, LocalDateTime dateTime, boolean isAllChat) {
        if (this.getSelectedChat().participantIds.contains(senderUri) || isAllChat) {
            // decrypt message and display the message
            String decryptedMessage = decrypt(msg);
            addToMessageList(decryptedMessage, senderUri, dateTime);
        }
    }

    @Override
    public void onRefresh() {
        super.onRefresh();
    }

    public LiveData<String> getNotificationMessage() { return notificationMessage; }
}
