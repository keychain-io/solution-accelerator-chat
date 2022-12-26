package io.keychain.chat.views.contacts;

import static io.keychain.common.Constants.CONTACT_ALREADY_EXISTS;
import static io.keychain.common.Constants.ERROR_SETTING_CHAT_RECIPIENT;
import static io.keychain.common.Constants.ERROR_SHOWING_CONVERSATION;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.keychain.chat.R;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.views.TabbedActivity;
import io.keychain.chat.views.qrcode.QrCodeActivity;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.common.Constants;
import io.keychain.mobile.util.Utils;

@UiThread
public class ContactMainFragment extends Fragment {
    private static final String TAG = "ContactMainFragment";
    public static final String PAIRING_USING_TRUSTED_DIRECTORY = "Pairing using trusted directory. Please wait ...";
    public static final String ID = "id";
    public static final String ERROR_WHILE_PARSING_QR_CODE_JSON = "Error while parsing QR Code JSON";
    private ContactViewAdapter adapter;
    private ListView listView;
    private ActivityResultLauncher<Intent> launcher;
    private TabbedViewModel viewModel;
    private Bitmap bm;
    private String payR;
    private Context context;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ContactMainFragment() {
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
        listView = view.findViewById(R.id.contactList);
        adapter = new ContactViewAdapter(requireActivity(), R.layout.contact_row, new ArrayList<>());
        listView.setAdapter(adapter);

        FloatingActionButton buttonTrustedDirectory = view.findViewById(R.id.buttonTrustedDirectory);
        buttonTrustedDirectory.setOnClickListener(v -> {
            executorService.execute(() -> {
                viewModel.pairUsingTrustedDirectory();
            });

            Toast.makeText(context, PAIRING_USING_TRUSTED_DIRECTORY, Toast.LENGTH_LONG).show();
        });

        FloatingActionButton buttonQrScanner = view.findViewById(R.id.buttonAddContactQr);
        buttonQrScanner.setOnClickListener(v -> launcher.launch(new Intent(getActivity(), QrCodeActivity.class)));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                try {
                    FragmentManager fm = getFragmentManager();

                    User user = adapter.getItem(position);

                    Log.d(TAG, "Selecting contact at position " + position);

                    showConversationFragment(user);
                } catch (Exception e) {
                    Log.e(TAG, ERROR_SETTING_CHAT_RECIPIENT, e);
                }
            }
        });

        // Live data observe
        viewModel.getUserContacts().observe(getViewLifecycleOwner(), contacts -> {
            // TODO: there is a case getActivity() is null if menu changes off ContactsMainFragment at the same time contacts list is updated
            adapter = new ContactViewAdapter(requireActivity(), R.layout.contact_row, contacts);
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

                JSONObject jsonObject = new JSONObject();

                try {
                    tvName.setText(persona.getName());
                    tvSName.setText(persona.getSubName());
                    String strURI = persona.getUri().toString().substring(0, 14) + "....";
                    tvUri.setText(strURI);

                    jsonObject.put("id", persona.getUri().toString());
                    jsonObject.put("firstName", persona.getName());
                    jsonObject.put("lastName", persona.getSubName());
                } catch (Exception e) {
                    Log.e(TAG, "Error getting persona data: " + e.getMessage());
                }

                payR = jsonObject.toString();

                Log.i(TAG, "QR Code data:");
                Log.i(TAG, payR);

                try {
                    bm = Utils.GetQrCode(payR, 300, 300);
                } catch (Exception e) {
                    Log.e(TAG, "Exception getting QR code: " + e.getMessage());
                }

                imageView.setImageBitmap(bm);
            }
        });

        // make a QR scan launcher
        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent intent = result.getData();
            if (intent != null && intent.hasExtra(QrCodeActivity.JSON_EXTRA)) {
                String json = intent.getStringExtra(QrCodeActivity.JSON_EXTRA);
                if (!json.isEmpty()) {
                    try {
                        JSONObject jobj = new JSONObject(json);
                        String url = jobj.getString(ID);

                        if (viewModel.contactExists(url)) {
                            Toast.makeText(getActivity(), CONTACT_ALREADY_EXISTS, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Toast.makeText(getActivity(), json, Toast.LENGTH_SHORT).show();

                        viewModel.pair(url);
                    } catch (JSONException e){
                        Log.e(TAG, ERROR_WHILE_PARSING_QR_CODE_JSON);
                    }
                }
            }
        });
    }

    private void showConversationFragment(User user) {
        try {
            TabbedActivity tabbedActivity = (TabbedActivity) ContactMainFragment.this.context;
            viewModel.setChatRecipient(user);

            tabbedActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tabbedActivity.showConversationView();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, ERROR_SHOWING_CONVERSATION, e);
            Toast.makeText(context, ERROR_SHOWING_CONVERSATION, Toast.LENGTH_SHORT).show();
        }
    }
}
