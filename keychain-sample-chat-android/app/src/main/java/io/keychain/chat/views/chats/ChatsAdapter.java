package io.keychain.chat.views.chats;

import static io.keychain.chat.R.id;
import static io.keychain.chat.R.layout;
import static io.keychain.common.Constants.UNABLE_TO_GET_ACTIVE_PERSONA_OR_IT_S_URI;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import io.keychain.chat.models.chat.Chat;

public class ChatsAdapter extends ArrayAdapter<ChatsAdapter.ChatRoomDetails> {
    private static final String TAG = "ChatsAdapter";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(Locale.JAPAN).withZone(ZoneId.systemDefault());

    public static class ChatRoomDetails {
        public ChatRoomDetails(Chat c, String n, String l) { this.chat = c; this.name = n; this.lastMessage = l; }
        public Chat chat;
        public String name;
        public String lastMessage;
    }

    public ChatsAdapter(Context context, List<ChatRoomDetails> chatRooms){
        super(context, layout.chats_row, chatRooms);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null){
            convertView = LayoutInflater.from(getContext()).inflate(layout.chats_row, parent, false);
        }

        try {
            ChatRoomDetails chatRoomDetails = getItem(position);
            if (chatRoomDetails == null) {
                Log.e(TAG, "Could not retrieve chat room details for position");
            } else {
                //ImageView imageView = convertView.findViewById(id.profile_pic);
                TextView userName = convertView.findViewById(id.chatName);
                TextView lastMsg = convertView.findViewById(id.lastMessage);
                TextView time = convertView.findViewById(id.messageTime);

                userName.setText(chatRoomDetails.name);
                lastMsg.setText(chatRoomDetails.lastMessage);

                time.setText(formatter.format(chatRoomDetails.chat.timestamp.toLocalTime()));
            }
        } catch (Exception e) {
            Log.w(TAG, UNABLE_TO_GET_ACTIVE_PERSONA_OR_IT_S_URI, e);
        }

        return convertView;
    }
}
