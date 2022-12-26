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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

import io.keychain.chat.models.PersonaLoginStatus;
import io.keychain.chat.models.chat.User;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.core.Persona;
import io.keychain.chat.R;
import io.keychain.chat.views.TabbedActivity;
import io.keychain.mobile.BaseActivity;

/**
 * Login and Persona Creation activity.
 */
public class PersonaActivity extends BaseActivity {
    private static final String TAG = "PersonaActivity";
    private ListView mainListView;
    private PersonaViewAdapter listAdapter;
    private TextView noPersonasTextView;
    private MaterialButton createPersona;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_persona);

        mainListView = findViewById(R.id.listPersonas);
        noPersonasTextView = findViewById(R.id.noPersonasTextView);
        createPersona = findViewById(R.id.createPersonaButton);

        listAdapter = new PersonaViewAdapter(this, R.layout.persona_row, new ArrayList<>());
        mainListView.setAdapter(listAdapter);

        ItemTouchHelper personaItemTouchHelper =
                new ItemTouchHelper(new PersonaItemTouchHelper(listAdapter));
        //personaItemTouchHelper.attachToRecyclerView(mainListView);

        // initially assume no personas
        noPersonasTextView.setVisibility(VISIBLE);
        mainListView.setVisibility(INVISIBLE);

        // Set the visibilities of the layouts
        createPersona.setEnabled(true);
        createPersona.setClickable(true);

        viewModel = new ViewModelProvider(this).get(TabbedViewModel.class);

        viewModel.isPersonaConfirmed().observe(this, confirmed -> {
            if (confirmed == PersonaLoginStatus.OK) {
                Toast.makeText(this, "Log in OK", Toast.LENGTH_SHORT).show();
            } else if (confirmed == PersonaLoginStatus.FAILURE) {
                Toast.makeText(this, "Can't log in with that persona yet", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getActivePersona().observe(this, persona -> {
            if (persona != null) {
                Intent intent = new Intent(this, TabbedActivity.class);
                try {
                    String uri = persona.getUri().toString();
                    Log.d(TAG, "Starting TabbedActivity with URI " + uri);
                    intent.putExtra(EXTRAS_URI, uri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // tabbed activity is the new task root
                    startActivity(intent);
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Error getting URI, so will not start TabbedActivity: " + e.getMessage());
                }
            }
        });

        viewModel.getUserPersonas().observe(this, list -> {
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
            User user = (User) adapter.getItemAtPosition(position);
            Persona persona = viewModel.findPersona(user.uri);

            if (persona != null) {
                viewModel.setActivePersona(persona);

                // attempt to select the persona - live data will update whether success or failure
                viewModel.selectPersona(persona);
                return;
            }

            Log.w(TAG, "Error finding persona.");
        });
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
