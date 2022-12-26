package io.keychain.chat.views.settings;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import io.keychain.chat.R;

public class PreferencesFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.prefs, rootKey);
    }
}