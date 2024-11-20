package io.keychain.chat.views;

import android.app.Application;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import io.keychain.chat.KeychainApp;
import io.keychain.chat.MqttUseCase;
import io.keychain.chat.R;
import io.keychain.chat.models.chat.Chat;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.chat.viewmodel.TabbedViewModelFactory;
import io.keychain.chat.views.chats.ChatsFragment;
import io.keychain.chat.views.chats.ConversationFragment;
import io.keychain.chat.views.contacts.ContactMainFragment;
import io.keychain.chat.views.persona.PersonaActivity;
import io.keychain.chat.views.settings.SettingFragment;
import io.keychain.mobile.BaseActivity;
import io.keychain.mobile.util.ConnectionLiveData;
import io.keychain.mobile.util.Utils;

public class TabbedActivity extends BaseActivity {
    private static final String TAG = "TabbedActivity";
    private FragmentContainerView fragmentContainerView;
    private Intent thisIntent;
    private boolean backPressed;
    private ImageView buttonConversation;
    private ImageView buttonChats;
    private ImageView buttonSettings;
    private ImageView buttonContacts;
    private ImageView imageNetworkStatus;
    private TextView toolbarPersona;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tabbed);
        final Toolbar toolbar = findViewById(R.id.toolbar_tabbed);
        setSupportActionBar(toolbar);

        thisIntent = getIntent();

        Application application = getApplication();
        MqttUseCase mqttUseCase = new MqttUseCase(
                ((KeychainApp) application).getMqttRepository(),
                "keychain-chat",
                KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_MQTT_CHANNEL_PAIRING),
                KeychainApp.GetInstance().getApplicationProperty(KeychainApp.PROPERTY_MQTT_CHANNEL_CHATS)
        );
        viewModel = new ViewModelProvider(this, new TabbedViewModelFactory(application, mqttUseCase)).get(TabbedViewModel.class);

        viewModel.getActivePersona().observe(this, persona -> {
            try {
                toolbarPersona.setText(persona.getName());
            } catch (Exception e) {
                Log.e(TAG, "Error getting persona name: " + e.getMessage());
            }
        });

        fragmentContainerView = findViewById(R.id.main_fragment);
        setCurrentFragment(new ContactMainFragment());
        imageNetworkStatus = findViewById(R.id.tabbedNetworkStatus);
        toolbarPersona = findViewById(R.id.tabbedPersonaName);
        buttonConversation = findViewById(R.id.buttonConversation);
        buttonChats = findViewById(R.id.buttonChats);
        buttonContacts = findViewById(R.id.buttonContacts);
        buttonContacts.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.white, getTheme())));
        buttonSettings = findViewById(R.id.buttonSettings);

//        buttonConversation.setOnClickListener(v -> showConversationView());
        buttonChats.setOnClickListener(v -> showChatsView());
        buttonSettings.setOnClickListener(v -> showSettingsView());
        buttonContacts.setOnClickListener(v -> showContactsView());

        ConnectionLiveData connectionLiveData = new ConnectionLiveData(this);
        connectionLiveData.observe(this, connectionStatus -> {
            @AttrRes int color;
            @DrawableRes int icon;
            if (connectionStatus < 0) {
                color = R.attr.colorError;
                icon = R.drawable.baseline_wifi_off_24;
            } else {
                color = connectionStatus > 0 ? R.attr.colorOn : R.attr.colorNegotiating;
                icon = R.drawable.baseline_wifi_24;
            }
            imageNetworkStatus.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, color)));
            imageNetworkStatus.setImageDrawable(AppCompatResources.getDrawable(this, icon));
        });

        String activePersonaUri;
        if (!thisIntent.hasExtra(EXTRAS_URI)) {
            Log.e(TAG, "Can't start TabbedActivity without URI in Intent!");
            return;
        }

        activePersonaUri = thisIntent.getStringExtra(EXTRAS_URI);
        ((TabbedViewModel) viewModel).setChatPersona(activePersonaUri);
    }

    public void showContactsView() {
        if (!(getCurrentFragment() instanceof ContactMainFragment)) {
            setCurrentFragment(new ContactMainFragment());
            buttonConversation.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonChats.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonSettings.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonContacts.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorOnPrimary)));
            setLayout();
        }
    }

    public void showChatsView() {
        if (!(getCurrentFragment() instanceof ChatsFragment)) {
            setCurrentFragment(ChatsFragment.newInstance(((TabbedViewModel) viewModel).getActivePersonaUri()));
            buttonConversation.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonChats.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorOnPrimary)));
            buttonSettings.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonContacts.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            setLayout();
            ((TabbedViewModel) viewModel).refreshChats();
        }
    }

    public void showConversationView(Chat chat) {
        if (!(getCurrentFragment() instanceof ConversationFragment)) {
            ArrayList<String> participants = new ArrayList<>(chat.participantIds);
            setCurrentFragment(ConversationFragment.newInstance(participants));
            ((TabbedViewModel) viewModel).setChat(chat);
            buttonConversation.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorOnPrimary)));
            buttonChats.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonSettings.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonContacts.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            setLayout();
        }
    }

    public void showSettingsView() {
        if (!(getCurrentFragment() instanceof SettingFragment)) {
            setCurrentFragment(new SettingFragment());
            buttonConversation.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonChats.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            buttonSettings.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorOnPrimary)));
            buttonContacts.setImageTintList(ColorStateList.valueOf(Utils.GetThemeColor(this, R.attr.colorPending)));
            setLayout();
        }
    }

    private Fragment getCurrentFragment() {
        return fragmentContainerView.getFragment();
    }

    public void setCurrentFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();

//        // if it's one of the 3 icons, save it so when we press 'back' we return to it
//        if (fragment instanceof ConversationFragment || fragment instanceof ContactMainFragment || fragment instanceof SettingFragment) {
//            currentFragment = fragment;
//        }

        fragmentManager.beginTransaction().replace(R.id.main_fragment, fragment).commit();
        backPressed = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_wallet, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        setLayout();
        onBackPressed();
        return true;
    }

    private void setLayout() {
        final LinearLayout ll = findViewById(R.id.tabbedBottomNav);
        ll.setVisibility(View.VISIBLE);
        final ActionBar bar = getSupportActionBar();
        if (bar == null) return;
        bar.setDisplayHomeAsUpEnabled(false);
        bar.setDisplayShowHomeEnabled(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        backPressed = false;

        if (item.getItemId() == R.id.action_logout) {
            Intent intent = new Intent(thisContext, PersonaActivity.class);
            thisContext.startActivity(intent);
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();

        Intent i = getIntent();
        // check for EXTRAS_URI but don't set persona here
        if (i != null && i.hasExtra(EXTRAS_URI)) {
            ((TabbedViewModel) viewModel).openMqttChannel();
        } else {
            Log.e(TAG, "We have no URI in TabbedActivity - make sure we have an active persona");
            finishActivity(0);
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        if (thisIntent != null && thisIntent.hasExtra(EXTRAS_URI)) {
            ((TabbedViewModel) viewModel).setChatPersona(thisIntent.getStringExtra(EXTRAS_URI));
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Fragment cf = getCurrentFragment();
        setLayout();
        if (cf == null || backPressed) {
            backPressed = false;
            super.onBackPressed();
        } else {
            backPressed = true;
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
    }
}
