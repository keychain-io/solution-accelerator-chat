package io.keychain.chat.views.persona;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import io.keychain.core.Facade;
import io.keychain.core.PersonaStatus;
import io.keychain.chat.R;
import io.keychain.mobile.util.Utils;

public class PersonaViewAdapter extends ArrayAdapter<Facade> {
    private static final String TAG = "PersonaViewAdapter";
    private final int layoutResource;

    public PersonaViewAdapter(Context context, int layoutResource, List<Facade> personaList) {
        super(context, layoutResource, personaList);
        this.layoutResource = layoutResource;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view = convertView;

        if (view == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(getContext());
            view = layoutInflater.inflate(layoutResource, null);
        }

        Facade user = getItem(position);

        if (user != null) {
            TextView leftTextView = view.findViewById(R.id.nameText);
            TextView rightTextView = view.findViewById(R.id.subNameText);
            TextView statusView = view.findViewById(R.id.statusText);

            try {
                if (leftTextView != null) {
                    leftTextView.setText(user.getName());
                }

                if (rightTextView != null) {
                    rightTextView.setText(user.getSubName());
                }

                if (statusView != null) {
                    PersonaStatus status = Utils.getPersonaStatus(user.getStatus().getStatusCode());
                    switch (status) {
                        case CREATED:
                            statusView.setText(R.string.persona_status_created);
                            statusView.setTextColor(Utils.GetThemeColor(getContext(), R.attr.colorInit));
                            break;
                        case FUNDING:
                            statusView.setText(R.string.persona_status_funding);
                            statusView.setTextColor(Utils.GetThemeColor(getContext(), R.attr.colorNegotiating));
                            break;
                        case BROADCASTED:
                            statusView.setText(R.string.persona_status_broadcasted);
                            statusView.setTextColor(Utils.GetThemeColor(getContext(), R.attr.colorNegotiating));
                            break;
                        case CONFIRMING:
                            statusView.setText(R.string.persona_status_confirming);
                            statusView.setTextColor(Utils.GetThemeColor(getContext(), R.attr.colorNegotiating));
                            break;
                        case CONFIRMED:
                            statusView.setText(R.string.persona_status_confirmed);
                            statusView.setTextColor(Utils.GetThemeColor(getContext(), R.attr.brandingConfirmedStatus));
                            break;
                        case EXPIRED:
                            statusView.setText(R.string.persona_status_expired);
                            statusView.setTextColor(Utils.GetThemeColor(getContext(), R.attr.colorOff));
                            break;
                        case EXPIRING:
                            statusView.setText(R.string.persona_status_expiring);
                            statusView.setTextColor(Utils.GetThemeColor(getContext(), R.attr.colorOff));
                            break;
                        default:
                            statusView.setText(R.string.persona_status_unknown);
                            statusView.setTextColor(Utils.GetThemeColor(getContext(), R.attr.colorOff));
                    }
                }
            }
            catch (Exception e){
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }
        return view;
    }

    public void deletePersona(int position) {

    }

    public void editItem(int position) {

    }
}
