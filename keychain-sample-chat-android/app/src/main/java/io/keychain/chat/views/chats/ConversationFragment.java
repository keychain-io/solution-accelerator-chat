package io.keychain.chat.views.chats;

import static io.keychain.common.Constants.ALL;
import static io.keychain.common.Constants.ERROR_GETTING_PLATFORM_USER_FOR;
import static io.keychain.common.Constants.SOMETHING_WENT_WRONG;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.time.LocalDateTime;

import io.keychain.chat.R;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.ChatMessage;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.chat.views.TabbedActivity;
import io.keychain.chat.views.contacts.ContactMainFragment;
import io.keychain.mobile.util.Utils;

@UiThread
public class ConversationFragment extends Fragment implements MessageInput.InputListener {
    private static final String TAG = "ConversationFragment";
    public static final String NO_CHAT_SELECTED_OR_CREATED = "No chat selected or created.";
    public static final String UNABLE_TO_SEND_MESSAGE = "Unable to send message. Please check the logs.";
    public static final String UNABLE_TO_GET_MY_URI = "Unable to get my uri.";
    private TabbedViewModel viewModel;
    private Context context;

    // GUI models
    private MessagesList messageList;
    private ImageLoader imageLoader;

    private User me = new User("0", "me", "", 5, null, "");

    public ConversationFragment() {
        // Required empty public constructor
    }

    public static ContactMainFragment newInstance() {
        return new ContactMainFragment();
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
        Log.d(TAG, "onCreateView()");

        View view = inflater.inflate(R.layout.conversation_fragment, container, false);

        try {
            this.imageLoader = (imageView, url, obj) -> {
                //Picasso.get().load(url).into(imageView);
            };

            viewModel = new ViewModelProvider(requireActivity()).get(TabbedViewModel.class);

            messageList = view.findViewById(io.keychain.chat.R.id.messagesList);
            viewModel.messageAdapter = new MessagesListAdapter<>(this.me.getId(), imageLoader);
            messageList.setAdapter(viewModel.messageAdapter);

            MessageInput input = view.findViewById(R.id.input);
            input.setInputListener(this);

            if (viewModel.getChatRecipient() == null) {
                // No chat recipient was selected. So default to ALL chat
                User user = viewModel.getUserMap().get(ALL);
                viewModel.setChatRecipient(user);

                if (user == null) {
                    throw new Exception(ERROR_GETTING_PLATFORM_USER_FOR + ALL);
                }
            }

            for (ChatMessage chatMessage : viewModel.getMessages()) {
                viewModel.displayMessage(chatMessage.msg,
                                         chatMessage.senderId,
                                         Utils.getDateTimeFromEpoc(chatMessage.timestamp),
                                         Utils.isAllChat(chatMessage));
            }
        } catch (Exception e) {
            Log.e(TAG, SOMETHING_WENT_WRONG, e);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView()");

        try {
            viewModel.setChatRecipient(null);
        } catch (Exception e) {
            Log.w(TAG, "Error resetting selected chat.", e);
        }

        super.onDestroyView();
    }

    @Override
    public boolean onSubmit(CharSequence input) {

        Log.d(TAG, "onSubmit() called with input: " + input.toString());
        String msg = input.toString();

        User recipient = viewModel.getChatRecipient();
        Chat selectedChat = viewModel.getSelectedChat();

        String myUri;

        try {
            myUri = viewModel.getActivePersona().getValue().getUri().toString();
        } catch (Exception e) {
            Toast.makeText(context, UNABLE_TO_GET_MY_URI, Toast.LENGTH_SHORT).show();
            return false;
        }

        String senderUri = myUri;
        String receiverUri = myUri.equals(selectedChat.participantIds.get(0))
                ? selectedChat.participantIds.get(1)
                : selectedChat.participantIds.get(0);

        if (viewModel.sendMessage(senderUri, receiverUri, msg)) {
            viewModel.addToMessageList(msg, senderUri, LocalDateTime.now());
        } else {
            Toast.makeText(context, UNABLE_TO_SEND_MESSAGE, Toast.LENGTH_SHORT).show();
            return false;
        }

        hideKeyboard((TabbedActivity) ConversationFragment.this.context);

        return true;
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
