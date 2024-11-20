package io.keychain.chat.services.database;

import static io.keychain.common.Constants.ALL;
import static io.keychain.common.Constants.CHATS;
import static io.keychain.common.Constants.CHATS_DB;
import static io.keychain.common.Constants.CHAT_ID;
import static io.keychain.common.Constants.CHAT_ID_IS_NOT_VALID;
import static io.keychain.common.Constants.ERROR_GETTING_CHATS_FOR_SENDER_ID;
import static io.keychain.common.Constants.ERROR_GETTING_MESSAGES_FOR_CHAT_ID;
import static io.keychain.common.Constants.ERROR_GETTING_PLATFORM_USERS;
import static io.keychain.common.Constants.ERROR_GETTING_PLATFORM_USER_FOR;
import static io.keychain.common.Constants.ERROR_GETTING_PLATFORM_USER_FOR_RECORD_ID;
import static io.keychain.common.Constants.ERROR_INSERTING_CHAT;
import static io.keychain.common.Constants.ERROR_SAVING_MESSAGE;
import static io.keychain.common.Constants.ERROR_SAVING_USER_PROFILE;
import static io.keychain.common.Constants.ERROR_UPDATING_CHAT;
import static io.keychain.common.Constants.ERROR_UPDATING_USER_PROFILE;
import static io.keychain.common.Constants.FIRST_NAME;
import static io.keychain.common.Constants.GETTING_ALL_MESSAGES_FOR_CHAT_ID;
import static io.keychain.common.Constants.GETTING_CHAT_FOR_SENDER_RECEIVER;
import static io.keychain.common.Constants.GETTING_CHAT_USERS;
import static io.keychain.common.Constants.GETTING_MESSAGE_FOR_RECORD_ID;
import static io.keychain.common.Constants.GETTING_PLATFORM_USER_FOR_ID;
import static io.keychain.common.Constants.ID;
import static io.keychain.common.Constants.INSERTING_CHAT_USER;
import static io.keychain.common.Constants.INSERTING_CHAT;
import static io.keychain.common.Constants.INSERTING_MESSAGE;
import static io.keychain.common.Constants.INVALID_RECEIVER_ID;
import static io.keychain.common.Constants.LAST_MSG;
import static io.keychain.common.Constants.LAST_NAME;
import static io.keychain.common.Constants.MESSAGES;
import static io.keychain.common.Constants.MSG;
import static io.keychain.common.Constants.NO_DATABASE_CONNECTION;
import static io.keychain.common.Constants.PARTICIPANT_IDS;
import static io.keychain.common.Constants.PARTICIPANT_IDS_MUST_CONTAIN_2_ELEMENTS;
import static io.keychain.common.Constants.PHOTO;
import static io.keychain.common.Constants.RECEIVER_ID;
import static io.keychain.common.Constants.SENDER_ID;
import static io.keychain.common.Constants.SENDER_ID_IS_NOT_VALID;
import static io.keychain.common.Constants.SEND_OR_RCVD;
import static io.keychain.common.Constants.SOURCE;
import static io.keychain.common.Constants.STATUS;
import static io.keychain.common.Constants.SUCCESSFULLY_INSERTED_CHAT;
import static io.keychain.common.Constants.SUCCESSFULLY_INSERTED_CHAT_USER;
import static io.keychain.common.Constants.SUCCESSFULLY_INSERTED_MESSAGE;
import static io.keychain.common.Constants.SUCCESSFULLY_UPDATED_CHAT;
import static io.keychain.common.Constants.SUCCESSFULLY_UPDATED_USER_PROFILE;
import static io.keychain.common.Constants.TIMESTAMP;
import static io.keychain.common.Constants.UNABLE_TO_SAVE_MESSAGE_TO_DB;
import static io.keychain.common.Constants.UPDATING_CHAT;
import static io.keychain.common.Constants.UPDATING_USER_PROFILE;
import static io.keychain.common.Constants.URI;
import static io.keychain.common.Constants.USERS;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.keychain.chat.interfaces.ChatRepository;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.ChatDirection;
import io.keychain.chat.models.chat.ChatMessage;
import io.keychain.chat.models.chat.User;
import io.keychain.mobile.util.Utils;

public class SQLiteDBService extends SQLiteOpenHelper implements ChatRepository {
    private static final String TAG = "SQLiteDBService";
    public static final String GETTING_PLATFORM_USER = "Getting platform user: ";
    public static final String GETTING_ALL_CHATS_FOR_SENDER_ID = "Getting all chats for senderId: ";
    public static final String CHATS_SQL = "chats.sql";
    public static final String ATTEMPTING_TO_COPY_DATABASE = "Attempting to copy database";
    public static final int DB_VERSION = 1;
    public static final String DATABASE_CANNOT_BE_OPENED = "For some reason the Database cannot be opened";
    public static final String FAILED_TO_OPEN_CHAT_DATABASE = "Failed to open chat database.";
    public static final String ERROR_CREATING_DATABASE_TABLES = "Error creating database tables.";
    public static final String CREATING_DATABASE_TABLES = "Creating database tables.";
    public static final String ERROR_DELETING_DATABASE_FILE = "Error deleting database file.";
    public static final String DATABASES = "/databases/";
    public static final String DATA_DATA = "/data/data/";
    public static final String GETTING_ALL_CHAT_MESSAGES_WHERE_PARTICIPANT_IS = "Getting all chat messages where participant is: ";
    public static final String ERROR_GETTING_ALL_CHAT_MESSAGES_WHERE_PARTICIPANT_IS = "Error getting all chat messages where participant is: ";

    Context context;
    AssetManager assetManager;
    SQLiteDatabase db;
    boolean dbInitialized = false;
    final String dbLocation;

    public SQLiteDBService(Context context) {
        super(context, CHATS_DB, null, DB_VERSION);

        if (android.os.Build.VERSION.SDK_INT >= 17) {
            dbLocation = context.getApplicationInfo().dataDir + DATABASES;
        } else {
            dbLocation = DATA_DATA + context.getPackageName() + DATABASES;
        }

        this.context = context;
        this.assetManager = context.getAssets();

        try {
            if (!ifDBExists()) {
                db = getWritableDatabase();
                Log.i(TAG, "Created database at: " + db.getPath());
            } else {
                openDatabase();
                Log.i(TAG, "Opened database at: " + db.getPath());
            }
        } catch (Exception e) {
            Log.e(TAG, FAILED_TO_OPEN_CHAT_DATABASE, e);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            Log.i(TAG, CREATING_DATABASE_TABLES);
            String script = getSqlScript().replace("\n", "");
            Log.i(TAG, "Executing script: ");
            Log.i(TAG, script);

            // execSQL can only execute a single STATEMENT. So we have to split the
            // SQL file into individual queries and call execSQL several times
            for (String sql : script.split(";")) {
                if (sql.isEmpty())
                    continue;

                db.execSQL(sql);
            }

            dbInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, ERROR_CREATING_DATABASE_TABLES, e);
            Toast.makeText(context, ERROR_CREATING_DATABASE_TABLES, Toast.LENGTH_LONG).show();
            closeDatabase();
            deleteDatabase();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > oldVersion) {
            Log.d(TAG, ATTEMPTING_TO_COPY_DATABASE);
        }
    }

    public void openDatabase() throws Exception {
        if (db != null && db.isOpen()) {
            return;
        }

        String dbPath =  dbLocation + CHATS_DB;
        db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE);

        if(!db.isOpen()) {
            throw new Exception(DATABASE_CANNOT_BE_OPENED);
        }

        dbInitialized = true;
    }

    public void closeDatabase() {
        if (db != null) {
            close();
        }
    }

    private void deleteDatabase() {
        try {
            String dbPath =  context.getDatabasePath(CHATS_DB).getPath();
            Files.delete(Paths.get(dbPath));
        } catch (IOException e) {
            Log.w(TAG, ERROR_DELETING_DATABASE_FILE, e);
        }
    }

    /**
     * Check if the database file exists
     */
    private boolean ifDBExists() {
        String dbPath = dbLocation + CHATS_DB;

        Log.i(TAG, "Chat db path: " + dbPath);

        File db =  new File(dbPath);

        if (db.exists())
            return true;

        File dbDir = new File(db.getParent());

        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }

        return false;
    }

    private String getSqlScript() throws Exception {
        InputStream inputStream = assetManager.open(CHATS_SQL);

        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        inputStream.close();

        return new String(buffer);
    }

    @Override
    public Optional<Map<String, User>> getPlatformUsers(Set<String> filterBy) {
        try {
            Log.i(TAG, GETTING_CHAT_USERS);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Cursor cursor = db.rawQuery("SELECT * FROM users", null);
            Optional<Map<String, User>> map = getPlatformUsers(cursor);
            cursor.close();
            return map;
        } catch (Exception ex) {
            Log.e(TAG, ERROR_GETTING_PLATFORM_USERS, ex);
        }

        return Optional.empty();
    }

    @Override
    public Optional<User> getPlatformUser(String recordId) {
        try {
            Log.i(TAG, GETTING_PLATFORM_USER_FOR_ID + recordId);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Cursor cursor = db.rawQuery("SELECT * FROM users WHERE id = ?",
                                        new String[] {recordId});

            Optional<User> user = getPlatformUsers(cursor).flatMap(users -> users.values().stream().findFirst());
            cursor.close();
            return user;
        } catch (Exception ex) {
            Log.e(TAG, ERROR_GETTING_PLATFORM_USER_FOR_RECORD_ID + recordId, ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<User>> getPlatformUser(String firstName, String lastName) {
        try {
            Log.i(TAG, GETTING_PLATFORM_USER + firstName + " " + lastName);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Cursor cursor = db.rawQuery("SELECT * FROM users WHERE firstName = ? AND lastName = ?",
                                        new String[] {firstName, lastName});

            Optional<List<User>> users = getPlatformUsers(cursor)
                    .map(Map::values)
                    .map(ArrayList::new);
            cursor.close();

            return users;
        } catch (Exception ex) {
            Log.e(TAG, ERROR_GETTING_PLATFORM_USER_FOR + firstName + " " + lastName, ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> getPlatformUserByUri(String uri) {
        try {
            Log.i(TAG, GETTING_PLATFORM_USER + uri);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Cursor cursor = db.rawQuery("SELECT * FROM users WHERE uri = ?", new String[] {uri});

            Optional<User> user = getPlatformUsers(cursor).flatMap(users -> users.values().stream().findFirst());
            cursor.close();
            return user;
        } catch (Exception ex) {
            Log.e(TAG, ERROR_GETTING_PLATFORM_USER_FOR_RECORD_ID + uri, ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> saveUserProfile(String firstName, String lastName, int status, int source, String uri, Bitmap image) {
        try {
            Log.i(TAG, INSERTING_CHAT_USER + firstName + " " + lastName);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            User user = getPlatformUserByUri(uri).orElse(null);

            if (user != null) {
                // If user already exists, return the id
                return Optional.of(user.id);
            }

            String imagesPath = null;

            // Check if an image is passed through
            if (image != null) {
                imagesPath = saveToInternalStorage(image);
            }

            String id = UUID.randomUUID().toString();

            ContentValues contentValues = new ContentValues();

            contentValues.put(ID, id);
            contentValues.put(FIRST_NAME, firstName);
            contentValues.put(LAST_NAME, lastName);
            contentValues.put(STATUS, status);
            contentValues.put(SOURCE, source);
            contentValues.put(URI, getUriToUse(uri));
            contentValues.put(PHOTO, imagesPath);

            long rc = db.insert(USERS, null, contentValues);

            if (rc > -1) {
                Log.i(TAG, SUCCESSFULLY_INSERTED_CHAT_USER + firstName + " " + lastName);

                return Optional.of(id);
            }
        } catch (Exception ex) {
            Log.e(TAG, ERROR_SAVING_USER_PROFILE, ex);
            return Optional.empty();
        }

        Log.e(TAG, ERROR_SAVING_USER_PROFILE + firstName + " " + lastName);

        return Optional.empty();
    }

    private String getUriToUse(String uri) {
        // uri must be unique, the UUID will be replace later when the actual uri is
        // received by Keychain Gateway
        return (uri != null && !uri.trim().isEmpty()) ? uri : UUID.randomUUID().toString();
    }

    @Override
    public boolean updateUserProfile(String firstName, String lastName, int status, String uri) {
        try {
            Log.i(TAG, UPDATING_USER_PROFILE + firstName + " " + lastName);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return false;
            }

            Optional<User> user = getPlatformUserByUri(uri);
            if (user.isPresent()) {
                ContentValues contentValues = new ContentValues();

                contentValues.put(FIRST_NAME, firstName);
                contentValues.put(LAST_NAME, lastName);
                contentValues.put(STATUS, status);
                contentValues.put(URI, getUriToUse(uri));

                long rc = db.update(USERS,
                                    contentValues,
                                    "uri=?",
                                    new String[] { uri });

                if (rc > 0) {
                    Log.i(TAG, SUCCESSFULLY_UPDATED_USER_PROFILE + firstName + " " + lastName);

                    return true;
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, ERROR_UPDATING_USER_PROFILE, ex);
            return false;
        }

        Log.e(TAG, ERROR_UPDATING_USER_PROFILE + firstName + " " + lastName);

        return false;
    }

    @Override
    public Optional<List<Chat>> getAllChats(String senderId, Set<String> excludePersonaIds) {
        try {
            Log.i(TAG, GETTING_ALL_CHATS_FOR_SENDER_ID + senderId);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Log.i(TAG, "getAllChats: db path: " + db.getPath());
            // Get chats where the active persona is a participant
            Cursor cursor = db.rawQuery("SELECT * FROM chats WHERE participantIds LIKE ?",
                                        new String[] {"%" + senderId + "%"});


            // Filter out chats where my other personas are participants
            List<Chat> chats = getChats(cursor)
                    .orElse(new ArrayList<>())
                    .stream()
                    .filter(chat -> !excludePersonaIds.contains(chat.participantIds.get(0)) && !excludePersonaIds.contains(chat.participantIds.get(1)))
                    .collect(Collectors.toList());
            cursor.close();
            return Optional.of(chats);
        } catch (Exception ex) {
            Log.e(TAG, ERROR_GETTING_CHATS_FOR_SENDER_ID + senderId, ex);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Chat> getChat(String senderId, String receiverId) {
        try {
            Log.i(TAG, GETTING_CHAT_FOR_SENDER_RECEIVER);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Cursor cursor = db.rawQuery("SELECT * FROM chats WHERE participantIds LIKE ? AND participantIds LIKE ?",
                                        new String[] {"%" + senderId + "%", "%" + receiverId + "%"});

            Optional<Chat> chat = getChats(cursor).flatMap(chats -> chats.stream().findFirst());
            cursor.close();
            return chat;

        } catch (Exception ex) {
            Log.e(TAG, ERROR_GETTING_PLATFORM_USERS, ex);
        }

        return Optional.empty();
    }

    @Override
    public Optional<String> saveChat(Chat chat) {
        try {
            Log.i(TAG, INSERTING_CHAT);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            ContentValues contentValues = new ContentValues();

            contentValues.put(ID, chat.id.toUpperCase());

            String participants = String.join("|", chat.participantIds);

            contentValues.put(PARTICIPANT_IDS, participants);
            contentValues.put(LAST_MSG, chat.lastMsg);
            contentValues.put(TIMESTAMP, chat.timestamp.toString());

            long rc = db.insert(CHATS, null, contentValues);

            if (rc > -1) {
                Log.i(TAG, SUCCESSFULLY_INSERTED_CHAT);

                return Optional.of(chat.id);
            }
        } catch (Exception ex) {
            Log.e(TAG, ERROR_INSERTING_CHAT, ex);
            return Optional.empty();
        }

        Log.e(TAG, ERROR_INSERTING_CHAT);

        return Optional.empty();
    }

    @Override
    public boolean updateChat(String id, String lastMessage) {
        try {
            Log.i(TAG, UPDATING_CHAT);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return false;
            }

            Cursor cursor = db.rawQuery("SELECT * FROM chats WHERE id = ?",
                                        new String[] {id});

            int cursorCount = cursor.getCount();
            cursor.close();
            if (cursorCount > 0) {
                ContentValues contentValues = new ContentValues();

                contentValues.put(LAST_MSG, lastMessage);
                contentValues.put(TIMESTAMP, LocalDateTime.now().toString());

                long rc = db.update(CHATS, contentValues, "id=?", new String[]{id});

                if (rc > 0) {
                    Log.i(TAG, SUCCESSFULLY_UPDATED_CHAT + id);

                    return true;
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, ERROR_UPDATING_CHAT, ex);
            return false;
        }

        Log.e(TAG, ERROR_UPDATING_CHAT + id);

        return false;
    }

    @Override
    public Optional<List<ChatMessage>> getAllMessages(Chat chat) {
        try {
            Log.i(TAG, GETTING_ALL_MESSAGES_FOR_CHAT_ID + chat.id);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Cursor cursor = db.rawQuery("SELECT * FROM messages WHERE chatId = ?",
                                        new String[] {chat.id});

            Optional<List<ChatMessage>> messages = getMessages(cursor);
            cursor.close();
            return messages;
        } catch (Exception ex) {
            Log.e(TAG, ERROR_GETTING_MESSAGES_FOR_CHAT_ID + chat.id, ex);
        }

        return Optional.empty();
    }

    @Override
    public Optional<List<ChatMessage>> getAllMessages(String uri) {
        try {
            Log.i(TAG, GETTING_ALL_CHAT_MESSAGES_WHERE_PARTICIPANT_IS + uri);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Cursor cursor = db.rawQuery("SELECT * FROM messages WHERE senderId = ? OR receiverId = ?",
                                        new String[] {uri, uri});

            Optional<List<ChatMessage>> messages = getMessages(cursor);
            cursor.close();
            return  messages;
        } catch (Exception ex) {
            Log.e(TAG, ERROR_GETTING_ALL_CHAT_MESSAGES_WHERE_PARTICIPANT_IS + uri, ex);
        }

        return Optional.empty();
    }

    @Override
    public Optional<ChatMessage> getMessage(String recordId) {
        try {
            Log.i(TAG, GETTING_MESSAGE_FOR_RECORD_ID + recordId);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            Cursor cursor = db.rawQuery("SELECT * FROM messages WHERE id = ?",
                                        new String[] {recordId});

            Optional<ChatMessage> message = getMessages(cursor).flatMap(chatMessages -> chatMessages.stream().findFirst());
            cursor.close();
            return message;
        } catch (Exception ex) {
            Log.e(TAG, "Error getting message for recordId: " + recordId, ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> saveMessage(String chatId, String senderId, String msg, ChatDirection direction, Chat chat) {
        try {
            Log.i(TAG, INSERTING_MESSAGE);

            if (db == null || !dbInitialized) {
                Log.w(TAG, NO_DATABASE_CONNECTION);
                return Optional.empty();
            }

            if (senderId == null || senderId.isEmpty()) {
                Log.e(TAG, UNABLE_TO_SAVE_MESSAGE_TO_DB + SENDER_ID_IS_NOT_VALID);
                return Optional.empty();
            }

            if (chat.participantIds == null || chat.participantIds.size() < 2) {
                Log.e(TAG, UNABLE_TO_SAVE_MESSAGE_TO_DB + PARTICIPANT_IDS_MUST_CONTAIN_2_ELEMENTS);
                return Optional.empty();
            }

            String receiverId = chat.participantIds.get(1);

            if (receiverId.isEmpty()) {
                Log.e(TAG, UNABLE_TO_SAVE_MESSAGE_TO_DB + INVALID_RECEIVER_ID);
                return Optional.empty();
            }

            if (chat.id == null || chat.id.isEmpty()) {
                Log.e(TAG, UNABLE_TO_SAVE_MESSAGE_TO_DB + CHAT_ID_IS_NOT_VALID);
                return Optional.empty();
            }

            String id = chatId != null && !chatId.trim().isEmpty()
                    ? chatId : UUID.randomUUID().toString().toUpperCase();

            ContentValues contentValues = new ContentValues();

            contentValues.put(ID, id);
            contentValues.put(CHAT_ID, chat.id);
            contentValues.put(SEND_OR_RCVD, direction.getDirection());
            contentValues.put(SENDER_ID, senderId);
            contentValues.put(RECEIVER_ID, receiverId);
            contentValues.put(MSG, msg);
            contentValues.put(TIMESTAMP, Utils.getLongFromDateTime(LocalDateTime.now()));

            long rc = db.insert(MESSAGES, null, contentValues);

            if (rc > -1) {
                Log.i(TAG, SUCCESSFULLY_INSERTED_MESSAGE);

                return Optional.of(id);
            }
        } catch (Exception ex) {
            Log.e(TAG, ERROR_SAVING_MESSAGE, ex);
            return Optional.empty();
        }

        Log.e(TAG, ERROR_SAVING_MESSAGE);

        return Optional.empty();
    }

    @Override
    public Optional<String> savePhotoMessage(String senderUri, Image image, Chat chat) {
        return Optional.empty();
    }

    private String saveToInternalStorage(Bitmap bitmapImage) throws Exception {
        ContextWrapper cw = new ContextWrapper(context);
        // path to /data/data/yourApp/app_data/imageDir
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        // Create imageDir
        File imagePath = new File(directory, UUID.randomUUID().toString() + ".jpg");

        Log.i(TAG, "Saving image to: " + imagePath.getAbsolutePath());

        try (FileOutputStream fos = new FileOutputStream(imagePath)) {
            // Use the compress method on the BitMap object to write image to the OutputStream
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            Log.d(TAG, "Successfully saved image to: " + imagePath.getAbsolutePath());
        }

        return imagePath.getAbsolutePath();
    }

    private Bitmap loadImageFromStorage(String path) throws FileNotFoundException {
        return BitmapFactory.decodeStream(new FileInputStream(path));
    }

    private Optional<Map<String, User>>  getPlatformUsers(Cursor cursor) {
        Map<String, User> users = new HashMap<>();

        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                String uri = cursor.getString(6);

                if (uri == null) {
                    continue;
                }

                users.put(uri, getUser(cursor, uri));
            }
        }

        return !users.isEmpty() ? Optional.of(users) : Optional.empty();
    }

    @NonNull
    private User getUser(Cursor cursor, String uri) {
        return new User(cursor.getString(0).toUpperCase(),
                             cursor.getString(1),
                             cursor.getString(2),
                             cursor.getInt(3),
                             cursor.getInt(4),
                             cursor.getString(5),
                             cursor.getString(6));
    }

    private Optional<List<Chat>> getChats(Cursor cursor) {
        List<Chat> chats = new ArrayList<>();

        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                Chat chat = getChat(cursor);

                if (chat.participantIds.contains(ALL)) {
                    chats.add(0, chat);
                    continue;
                }

                chats.add(getChat(cursor));
            }
        }

        return !chats.isEmpty() ? Optional.of(chats) : Optional.empty();
    }

    @NonNull
    private Chat getChat(Cursor cursor) {
        Chat chat = new Chat();
        chat.id = cursor.getString(0);
        chat.participantIds = new ArrayList<>();

        String[] parts = cursor.getString(1).split("[|]");

        if (parts.length > 1) {
            chat.participantIds.add(parts[0]);
            chat.participantIds.add(parts[1]);
        }

        chat.lastMsg = cursor.getString(2);
        chat.timestamp = LocalDateTime.parse(cursor.getString(3));
        return chat;
    }

    private Optional<List<ChatMessage>> getMessages(Cursor cursor) {
        List<ChatMessage> messages = new ArrayList<>();

        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                try {
                    messages.add(getMessage(cursor));
                } catch (Exception e) {
                    Log.e(TAG, "Error getting message from db: ", e);
                }
            }
        }

        return !messages.isEmpty() ? Optional.of(messages) : Optional.empty();
    }

    private ChatMessage getMessage(Cursor cursor) {
        ChatMessage message = new ChatMessage();
        message.id = cursor.getString(0);
        message.chatId = cursor.getString(1);
        message.sendOrRcvd = ChatDirection.valueOf(cursor.getString(2));
        message.senderId = cursor.getString(3);
        message.receiverId = cursor.getString(4);
        message.imageUrl = cursor.getString(5);
        message.msg = cursor.getString(6);

        message.timestamp = Long.parseLong(cursor.getString(7));

        return message;
    }

}
