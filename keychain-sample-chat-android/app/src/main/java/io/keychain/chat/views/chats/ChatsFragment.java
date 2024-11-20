package io.keychain.chat.views.chats;

import static io.keychain.common.Constants.ERROR_SETTING_CHAT_RECIPIENT;
import static io.keychain.common.Constants.ON_DETACH;
import static io.keychain.common.Constants.ON_STOP;
import static io.keychain.common.Constants.ON_VIEW_CREATED;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.keychain.chat.R;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.chat.views.TabbedActivity;

public class ChatsFragment extends Fragment {
    private static final String TAG = "ChatsFragment";
    public static final String EXTRAS_URI = "uri";
    private TabbedViewModel viewModel;
    private Context context;
    private ChatsAdapter adapter;
    private String loggedInUserUri;

    public ChatsFragment() { }

    public static ChatsFragment newInstance(String uri)
    {
        ChatsFragment chatsFragment = new ChatsFragment();
        Bundle args = new Bundle();
        args.putString(EXTRAS_URI, uri);
        chatsFragment.setArguments(args);
        return chatsFragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        Log.d(TAG, "onAttach()");
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            loggedInUserUri = getArguments().getString(EXTRAS_URI, null);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TabbedViewModel.class);
        return inflater.inflate(R.layout.chats_fragment, container, false);
    }

    @Override
    public void onStop() {
        Log.d(TAG, ON_STOP);
        super.onStop();
    }

    @Override
    public void onDetach() {
        Log.d(TAG, ON_DETACH);
        super.onDetach();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG, ON_VIEW_CREATED);

        final ListView listView = view.findViewById(R.id.chatsList);

        // create a list of chat room details
        adapter = new ChatsAdapter(context, createChatRoomDetails(Collections.emptyList()));
        listView.setAdapter(adapter);

        viewModel.getChats().observe(this, chats -> {
            adapter = new ChatsAdapter(context, createChatRoomDetails(chats));
            listView.setAdapter(adapter);
        });

        listView.setOnItemClickListener((adapterView, view1, position, l) -> {
            try {
                Log.d(TAG, "Selecting chat at position " + position);
                final ChatsAdapter.ChatRoomDetails chat = adapter.getItem(position);
                if (chat == null) {
                    Log.e(TAG, "Error getting chat");
                    return;
                }

                TabbedActivity tabbedActivity = (TabbedActivity) ChatsFragment.this.context;

                // set chats
                viewModel.setChat(chat.chat);

                // switch fragments
                tabbedActivity.runOnUiThread(() -> tabbedActivity.showConversationView(chat.chat));
            } catch (Exception e) {
                Log.e(TAG, ERROR_SETTING_CHAT_RECIPIENT, e);
            }
        });
    }

    private List<ChatsAdapter.ChatRoomDetails> createChatRoomDetails(List<Chat> chats) {
        List<ChatsAdapter.ChatRoomDetails> details = new ArrayList<>(chats.size());
        for (Chat chat : chats) {
            final User user = viewModel.getUserNameForUri(chat
                            .participantIds
                            .stream()
                            .filter(s -> !s.equals(loggedInUserUri))
                            .findFirst()
                            .orElse(null));
            if (user == null) {
                Log.w(TAG, "Could not find user for chat ID " + chat.id);
            } else {
                details.add(new ChatsAdapter.ChatRoomDetails(
                        chat,
                        user.getName(),
                        viewModel.decrypt(chat.lastMsg)
                ));
            }
        }
        return details;
    }
}
