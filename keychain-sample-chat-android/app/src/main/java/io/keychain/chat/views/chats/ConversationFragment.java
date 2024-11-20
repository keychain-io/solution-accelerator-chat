package io.keychain.chat.views.chats;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.keychain.chat.R;
import io.keychain.chat.models.chat.Message;
import io.keychain.chat.models.chat.PairStatus;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.models.chat.UserSource;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.chat.views.TabbedActivity;

@UiThread
public class ConversationFragment extends Fragment implements MessageInput.InputListener {
    private static final String TAG = "ConversationFragment";
    public static final String EXTRAS_PARTICIPANTS = "participants";

    private TabbedViewModel viewModel;
    private Context context;
    private final Set<String> msgIds = new HashSet<>();

    private final List<String> participantIds = new ArrayList<>();

    // TODO: remove, use the actual persona user
    private final User me = new User("0", "me", "", PairStatus.PAIRED.getCode(), UserSource.DEFAULT.getCode(), "", "");

    public ConversationFragment() { }

    public static ConversationFragment newInstance(ArrayList<String> participantIds)
    {
        ConversationFragment fragment = new ConversationFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(EXTRAS_PARTICIPANTS, participantIds);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onAttach(@NonNull Context context) {
        Log.d(TAG, "onAttach()");
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            participantIds.addAll(Objects.requireNonNull(getArguments().getStringArrayList(EXTRAS_PARTICIPANTS)));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView()");

        View view = inflater.inflate(R.layout.conversation_fragment, container, false);

        try {
            ImageLoader imageLoader = (imageView, url, obj) -> {
                //Picasso.get().load(url).into(imageView);
            };

            viewModel = new ViewModelProvider(requireActivity()).get(TabbedViewModel.class);

            // GUI models
            MessagesList messageList = view.findViewById(R.id.messagesList);
            MessagesListAdapter<Message> messageAdapter = new MessagesListAdapter<>(this.me.getId(), imageLoader);
            messageList.setAdapter(messageAdapter);

            viewModel.getAllMessages().observe(this, messages -> {
                if (messages != null) {
                    msgIds.clear();
                    msgIds.addAll(messages.stream().map(Message::getId).collect(Collectors.toList()));
                    messageAdapter.clear();
                    messageAdapter.addToEnd(messages, true);
                }
            });
            viewModel.getLatestMessage().observe(this, message -> {
                if (message != null && !msgIds.contains(message.getId())) {
                    msgIds.add(message.getId());
                    messageAdapter.addToStart(message, true);
                }
            });

            MessageInput input = view.findViewById(R.id.input);
            input.setInputListener(this);
        } catch (Exception e) {
            Log.e(TAG, SOMETHING_WENT_WRONG, e);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView()");

        super.onDestroyView();
    }

    @Override
    public boolean onSubmit(CharSequence input) {
        Log.d(TAG, "onSubmit() called with input: " + input.toString());
        String msg = input.toString();

        try {
            viewModel.handleSubmittedMessage(msg, participantIds);
        } catch (Exception e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
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
