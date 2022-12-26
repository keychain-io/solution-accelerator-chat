package io.keychain.chat.views.chats;

import static io.keychain.common.Constants.ERROR_GETTING_PLATFORM_USER_FOR;
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
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.keychain.chat.R;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.chat.views.TabbedActivity;
import io.keychain.chat.views.contacts.ContactMainFragment;
import io.keychain.core.Persona;

public class ChatsFragment extends Fragment {
    private static final String TAG = "ChatsFragment";
    public static final String ERROR_SHOWING_CHAT = "Error showing chat. ";
    private TabbedViewModel viewModel;
    private Context context;

    private ListView listView;
    private ChatsAdapter adapter;

    public ChatsFragment() {
        // Required empty public constructor
    }

    public static ChatsFragment newInstance() {
        return new ChatsFragment();
    }

    @Override
    public void onAttach(Context context) {
        Log.d(TAG, "onAttach()");
        super.onAttach(context);
        this.context = context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TabbedViewModel.class);
        viewModel.loadChats();

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

        listView = view.findViewById(R.id.chatsList);
        adapter = new ChatsAdapter(context, viewModel);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                try {
                    Chat chat = adapter.getItem(position);

                    Log.d(TAG, "Selecting chat at position " + position);

                    showConversationView(chat);
                } catch (Exception e) {
                    Log.e(TAG, ERROR_SETTING_CHAT_RECIPIENT, e);
                }
            }

            private void showConversationView(Chat chat) {
                try {
                    TabbedActivity tabbedActivity = (TabbedActivity) ChatsFragment.this.context;
                    Persona persona = viewModel.getActivePersona().getValue();
                    String recipientId = !chat.participantIds.get(0).equals(persona.getUri().toString())
                            ? chat.participantIds.get(0)
                            : chat.participantIds.get(1);
                    User user = viewModel.getUserMap().get(recipientId);
                    viewModel.setChatRecipient(user);

                    if (user == null) {
                        throw new Exception(ERROR_GETTING_PLATFORM_USER_FOR + recipientId);
                    }

                    tabbedActivity.runOnUiThread(() -> tabbedActivity.showConversationView());
                } catch (Exception e) {
                    Log.e(TAG, ERROR_SHOWING_CHAT, e);
                }
            }
        });
    }
}
