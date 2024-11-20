package io.keychain.chat.views.contacts;

import static io.keychain.common.Constants.CONTACT_ALREADY_EXISTS;
import static io.keychain.common.Constants.ERROR_SETTING_CHAT_RECIPIENT;
import static io.keychain.common.Constants.ERROR_SHOWING_CONVERSATION;
import static io.keychain.mobile.util.Utils.QR_ID;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.keychain.chat.R;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.models.chat.UserSource;
import io.keychain.chat.views.TabbedActivity;
import io.keychain.chat.views.qrcode.QrCodeActivity;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.mobile.util.Utils;

@UiThread
public class ContactMainFragment extends Fragment {
    private static final String TAG = "ContactMainFragment";
    private static final String PAIRING_USING_TRUSTED_DIRECTORY = "Pairing using trusted directory. Please wait ...";
    private static final String ERROR_WHILE_PARSING_QR_CODE_JSON = "Error while parsing QR Code JSON";

    private ActivityResultLauncher<Intent> launcher;
    private TabbedViewModel viewModel;
    private Context context;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ContactMainFragment() { }

    public static ContactMainFragment newInstance() {
        return new ContactMainFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        Log.d(TAG, "onAttach()");
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView()");
        setRetainInstance(true);
        super.onCreate(savedInstanceState);

        return inflater.inflate(R.layout.contact_main_fragment, container, false);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onDetach");
        super.onDetach();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated()");

        viewModel = new ViewModelProvider(requireActivity()).get(TabbedViewModel.class);

        // Top segment
        TextView tvName = view.findViewById(R.id.nTxt);
        TextView tvSName = view.findViewById(R.id.snTxt);
        TextView tvUri = view.findViewById(R.id.uTxt);
        ImageView imageView = view.findViewById(R.id.contactQr);

        // Bottom segment
        ListView listView = view.findViewById(R.id.contactList);

        final ContactViewAdapter adapter = new ContactViewAdapter(requireActivity(), R.layout.contact_row, new ArrayList<>());
        listView.setAdapter(adapter);

        FloatingActionButton buttonTrustedDirectory = view.findViewById(R.id.buttonTrustedDirectory);
        buttonTrustedDirectory.setOnClickListener(v -> {
            executorService.execute(() -> viewModel.downloadTrustedDirectoryContacts());
            Toast.makeText(context, PAIRING_USING_TRUSTED_DIRECTORY, Toast.LENGTH_SHORT).show();
        });

        FloatingActionButton buttonQrScanner = view.findViewById(R.id.buttonAddContactQr);
        buttonQrScanner.setOnClickListener(v -> launcher.launch(new Intent(getActivity(), QrCodeActivity.class)));

        listView.setOnItemClickListener((adapterView, view1, position, l) -> {
            try {
                ChatUser user = adapter.getItem(position);
                // Only do something if we're paired
                if (user != null && user.isChattable) {
                    Log.d(TAG, "Selecting contact at position " + position);
                    showConversationFragment(user);
                }
            } catch (Exception e) {
                Log.e(TAG, ERROR_SETTING_CHAT_RECIPIENT, e);
            }
        });

        viewModel.getTrustedDirectoryResult().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Live data observe
        viewModel.getChatContacts().observe(getViewLifecycleOwner(), contacts -> {
            adapter.clear();
            adapter.addAll(contacts);
            listView.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        });

        viewModel.getActivePersona().observe(getViewLifecycleOwner(), persona -> {
            if (persona == null) {
                tvName.setText(R.string.contact_top_name);
                tvSName.setText(R.string.contact_top_subname);
                tvUri.setText(R.string.contact_top_uri);
                imageView.setImageResource(R.mipmap.ic_launcher);
            } else {
                try {
                    tvName.setText(persona.getName());
                    tvSName.setText(persona.getSubName());
                    String strURI = persona.getUri().toString().substring(0, 14) + "....";
                    tvUri.setText(strURI);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting persona data: " + e.getMessage());
                }

                try {
                    imageView.setImageBitmap(Utils.GetQrCode(persona, 300, 300));
                } catch (Exception e) {
                    Log.e(TAG, "Exception getting QR code: " + e.getMessage());
                }
            }
        });

        // make a QR scan launcher
        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent intent = result.getData();
            if (intent != null && intent.hasExtra(QrCodeActivity.JSON_EXTRA)) {
                String json = intent.getStringExtra(QrCodeActivity.JSON_EXTRA);
                if (json != null && !json.isEmpty()) {
                    try {
                        JSONObject jobj = new JSONObject(json);
                        String url = jobj.getString(QR_ID);

                        if (viewModel.contactExists(url)) {
                            Toast.makeText(getActivity(), CONTACT_ALREADY_EXISTS, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Toast.makeText(getActivity(), json, Toast.LENGTH_SHORT).show();

                        viewModel.pair(url, UserSource.QR_CODE);
                    } catch (JSONException e){
                        Log.e(TAG, ERROR_WHILE_PARSING_QR_CODE_JSON);
                    }
                }
            }
        });
    }

    private void showConversationFragment(ChatUser user) {
        try {
            TabbedActivity tabbedActivity = (TabbedActivity) ContactMainFragment.this.context;

            Chat chat = viewModel.setChatByFacade(user);
            if (chat == null) return;

            tabbedActivity.runOnUiThread(() -> tabbedActivity.showConversationView(chat));
        } catch (Exception e) {
            Log.e(TAG, ERROR_SHOWING_CONVERSATION, e);
            Toast.makeText(context, ERROR_SHOWING_CONVERSATION, Toast.LENGTH_SHORT).show();
        }
    }
}
