package io.keychain.chat.interfaces;

import android.graphics.Bitmap;
import android.media.Image;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.ChatDirection;
import io.keychain.chat.models.chat.ChatMessage;
import io.keychain.chat.models.chat.User;

public interface ChatRepository {

    Optional<Map<String, User>> getPlatformUsers(Set<String> filterBy);

    Optional<User> getPlatformUser(String recordId);

    Optional<List<User>> getPlatformUser(String firstName, String lastName);

    Optional<User> getPlatformUserByUri(String uri);

    // Returns the record id
    Optional<String> saveUserProfile(String firstName, String lastName, int status, int source, String uri, Bitmap image);

    boolean updateUserProfile(String firstName, String lastName, int status, String uri);

    Optional<List<Chat>> getAllChats(String senderId, Set<String> personaIds);

    Optional<Chat> getChat(String senderId, String receiverId);

    Optional<List<ChatMessage>> getAllMessages(Chat chat);

    Optional<List<ChatMessage>> getAllMessages(String uri);

    // Returns the record id
    Optional<String> saveChat(Chat chat);

    boolean updateChat(String id, String lastMessage);

    Optional<ChatMessage> getMessage(String recordId);

    // Returns the record id
    Optional<String> saveMessage(String chatId, String senderId, String msg, ChatDirection direction, Chat chat);

    // Returns the record id
    Optional<String> savePhotoMessage(String senderUri, Image image, Chat chat);
}
