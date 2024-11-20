package io.keychain.chat.views.persona;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

import io.keychain.chat.R;
import io.keychain.chat.models.PersonaLoginStatus;
import io.keychain.chat.viewmodel.PersonaViewModel;
import io.keychain.chat.views.TabbedActivity;
import io.keychain.core.Facade;
import io.keychain.core.Persona;
import io.keychain.mobile.BaseActivity;
import io.keychain.mobile.KeychainApplication;
import io.keychain.mobile.services.GatewayService;

/**
 * Login and Persona Creation activity.
 */
public class PersonaActivity extends BaseActivity {
    private static final String TAG = "PersonaActivity";
    private ListView mainListView;
    private PersonaViewAdapter listAdapter;
    private TextView noPersonasTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_persona);

        KeychainApplication app = (KeychainApplication) getApplication();
        try {
            if (app.getGatewayService() == null)
                app.setGatewayService(new GatewayService(this));
        } catch (Exception e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(thisContext);
            builder
                    .setTitle("Error creating gateway")
                    .setMessage(e.getMessage())
                    .setNeutralButton("Quit", (dialog, which) -> {
                        dialog.cancel();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    })
                    .setCancelable(false)
                    .create()
                    .show();
        }

        if (app.getGatewayService() == null) return;

        mainListView = findViewById(R.id.listPersonas);
        noPersonasTextView = findViewById(R.id.noPersonasTextView);
        final MaterialButton createPersona = findViewById(R.id.createPersonaButton);

        listAdapter = new PersonaViewAdapter(this, R.layout.persona_row, new ArrayList<>());
        mainListView.setAdapter(listAdapter);

        // initially assume no personas
        noPersonasTextView.setVisibility(VISIBLE);
        mainListView.setVisibility(INVISIBLE);

        // Set the visibilities of the layouts
        createPersona.setEnabled(true);
        createPersona.setClickable(true);

        viewModel = new ViewModelProvider(this).get(PersonaViewModel.class);

        ((PersonaViewModel) viewModel).isPersonaConfirmed().observe(this, confirmed -> {
            if (confirmed.personaLoginState == PersonaLoginStatus.PersonaLoginState.OK) {
                Toast.makeText(this, "Log in OK", Toast.LENGTH_SHORT).show();

                String uri = confirmed.personaUri;
                    Intent intent = new Intent(this, TabbedActivity.class);
                    Log.d(TAG, "Starting TabbedActivity with URI " + uri);
                    intent.putExtra(EXTRAS_URI, uri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // tabbed activity is the new task root
                    startActivity(intent);
                    finish();
            } else if (confirmed.personaLoginState == PersonaLoginStatus.PersonaLoginState.FAILURE) {
                Toast.makeText(this, "Can't log in with that persona yet", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getPersonas().observe(this, list -> {
            listAdapter.clear();
            listAdapter.addAll(list);
            listAdapter.notifyDataSetChanged();
            noPersonasTextView.setVisibility(list.isEmpty() ? VISIBLE : INVISIBLE);
            mainListView.setVisibility(list.isEmpty() ? INVISIBLE : VISIBLE);
        });

        // set Create callback
        createPersona.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fm = getSupportFragmentManager();
                PersonaDialog dialogFragment = new PersonaDialog();
                dialogFragment.show(fm, "createPersonaDialog");
                fm.executePendingTransactions();
                fm.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                        super.onFragmentViewDestroyed(fm, f);
                        fm.unregisterFragmentLifecycleCallbacks(this);
                    }
                }, false);
            }
        });

        // set Select callback
        mainListView.setOnItemClickListener((adapter, v, position, id) -> {
            Facade user = (Facade) adapter.getItemAtPosition(position);

            if (user != null) {
                // attempt to select the persona - live data will update whether success or failure
                ((PersonaViewModel) viewModel).selectPersona((Persona) user);
                return;
            }

            Log.w(TAG, "Error finding persona.");
        });

        // refresh personas
        viewModel.refreshPersonas();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
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
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }
}
