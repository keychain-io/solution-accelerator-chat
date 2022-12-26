package io.keychain.chat.views.persona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import io.keychain.chat.R;
import io.keychain.chat.models.CreatePersonaResult;
import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.mobile.util.Utils;

/**
 * Simple full screen DialogFragment opened when a user clicks on a contact.
 * The dialog allows for rename and delete
 */
public class PersonaDialog extends DialogFragment {
    private static final String TAG = "PersonaDialog";
    private TabbedViewModel viewModel;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_persona_dialog, container, false);
        getDialog().setTitle("Create Persona");
        return rootView;
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        EditText evLname = view.findViewById(R.id.editTextTextPersonLName);
        EditText evFname = view.findViewById(R.id.editTextTextPersonFName);
        evLname.setText("");
        evFname.setText("");

        Button cxlButton = view.findViewById(R.id.cancelCreateButton);
        cxlButton.setOnClickListener(view1 -> {
            getDialog().dismiss();
        });

        viewModel = new ViewModelProvider(requireActivity()).get(TabbedViewModel.class);

        Button createButton = view.findViewById(R.id.createPersonaButton);

        createButton.setOnClickListener(view12 -> {
            // sanitize
            String lastName = Utils.SanitizeInput(evLname.getText().toString());
            String firstName = Utils.SanitizeInput(evFname.getText().toString());

            CreatePersonaResult cpr = viewModel.createPersona(firstName, lastName);

            if (cpr.created) {
                Toast.makeText(getActivity(), "Persona created", Toast.LENGTH_SHORT).show();
                getDialog().dismiss();
            } else {
                Toast.makeText(getActivity(), "Persona not created: " + cpr.message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}