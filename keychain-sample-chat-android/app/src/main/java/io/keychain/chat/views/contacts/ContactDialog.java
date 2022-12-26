package io.keychain.chat.views.contacts;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import io.keychain.chat.viewmodel.TabbedViewModel;
import io.keychain.core.Contact;
import io.keychain.chat.R;
import io.keychain.exceptions.NoUri;

/**
 * Simple full screen DialogFragment opened when a user clicks on a contact.
 * The dialog allows for rename and delete
 */
public class ContactDialog extends DialogFragment {
    private static final String TAG = "ContactDialog";
    private Contact selectedContact;

    public ContactDialog(Contact selectedContact) {
        this.selectedContact = selectedContact;
    }

    @Override
    public void onAttach(Context context) {
        Log.d(TAG, "onAttach()");
        super.onAttach(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView()");
        setRetainInstance(true);
        super.onCreate(savedInstanceState);

        return inflater.inflate(R.layout.fragment_contact_dialog, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        getDialog().setTitle("Simple Dialog");

        EditText evLname = view.findViewById(R.id.lastNameEditTextPersona);
        EditText evFname = view.findViewById(R.id.firstNameEditTextPersona);
        TextView evUri = view.findViewById(R.id.UriTextPersona);
        Button delButton = view.findViewById(R.id.deleteButton);

        try {
            try {
                evUri.setText(selectedContact.getUri().toString());
            } catch (NoUri e) {
                e.printStackTrace();
            }
            evLname.setText(selectedContact.getName());
            evFname.setText(selectedContact.getSubName());
        } catch (Exception e) {
            Log.e(TAG, "Error getting contact details: " + e.getMessage());
        }

        TabbedViewModel viewModel = new ViewModelProvider(requireActivity()).get(TabbedViewModel.class);

        delButton.setOnClickListener(view1 -> {
            String lastName1 = evLname.getText().toString();
            new AlertDialog.Builder(view1.getContext())
                    .setTitle("Confirm")
                    .setMessage("Do you really want to delete " + lastName1 + " ?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                        Log.i(TAG,"Trying to delete the contact " + lastName1);
                        viewModel.deleteContact(selectedContact);
                        getDialog().dismiss();
                    })
                    .setNegativeButton(android.R.string.no, null).show();

        });

        Button renButton = view.findViewById(R.id.renameButton);
        renButton.setOnClickListener(view12 -> {
            String firstName1 = evFname.getText().toString();
            String lastName1 = evLname.getText().toString();
            new AlertDialog.Builder(view12.getContext())
                    .setTitle("Confirm")
                    .setMessage("Do you really want to rename " + lastName1 + " ?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                        Log.i(TAG,"Trying to rename the contact " + lastName1);
                        viewModel.modifyContact(selectedContact, firstName1, lastName1);
                        getDialog().dismiss();
                    })
                    .setNegativeButton(android.R.string.no, null).show();
        });
    }
}