package io.keychain.chat.views.chats;

import static io.keychain.chat.R.*;
import static io.keychain.common.Constants.ALL;
import static io.keychain.common.Constants.UNABLE_TO_GET_ACTIVE_PERSONA_OR_IT_S_URI;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.keychain.chat.R;

import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.common.Constants;
import io.keychain.core.Persona;
import io.keychain.core.PersonaStatus;

public class ChatsAdapter extends ArrayAdapter<Chat> {
    private static final String TAG = "ChatsAdapter";
    private TabbedViewModel viewModel;
    private Context context;
    private List<Chat> chatsList;
    private Map<String, User> usersMap;

    public ChatsAdapter(Context context, TabbedViewModel viewModel){
        super(context, layout.chats_row, viewModel.getChatList());

        this.context = context;
        this.viewModel = viewModel;
        this.chatsList = viewModel.getChatList();
        this.usersMap = viewModel.getUserMap();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null){
            convertView = LayoutInflater.from(getContext()).inflate(layout.chats_row, parent, false);
        }

        try {
            Chat chat = getItem(position);

            Persona persona = viewModel.getActivePersona().getValue();
            String myUri = persona.getUri().toString();

            User user;

            if (myUri.equals(chat.participantIds.get(0))) {
                user = usersMap.get(chat.participantIds.get(1));
            } else {
                user = usersMap.get(chat.participantIds.get(0));
            }

            if (user != null) {
                //ImageView imageView = convertView.findViewById(id.profile_pic);
                TextView userName = convertView.findViewById(id.chatName);
                TextView lastMsg = convertView.findViewById(id.lastMessage);
                TextView time = convertView.findViewById(id.messageTime);

                //imageView.setImageResource();
                userName.setText(user.getName());

                lastMsg.setText((chat.lastMsg != null && !chat.lastMsg.trim().isEmpty())
                                        ? viewModel.decrypt(chat.lastMsg)
                                        : "");

                time.setText(chat.timestamp.toLocalTime().toString());
            } else {
                Toast.makeText(context, "Error getting chat user.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.w(TAG, UNABLE_TO_GET_ACTIVE_PERSONA_OR_IT_S_URI, e);
        }

        return convertView;
    }
}
