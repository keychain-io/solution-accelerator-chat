package io.keychain.chat.views.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.keychain.chat.KeychainApp;
import io.keychain.chat.R;
import io.keychain.mobile.util.Utils;

public class SettingFragment extends Fragment {
    private static final String TAG = "SettingFragment";
    private View view;
    private SpannableString settingsText;
    private SpannableString keychainText;

    public static SettingFragment newInstance() {
        SettingFragment fragment = new SettingFragment();
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        Log.d(TAG, "onAttach()");
        super.onAttach(context);

        String st = KeychainApp.GetInstance().loadAssetString("application.properties");
        if (st == null) {
            st = "Error reading settings";
        }
        settingsText = spannify(st);

        String kt = KeychainApp.GetInstance().loadAssetString("keychain.cfg");
        if (kt == null) {
            kt = "Error reading keychain.cfg";
        }
        keychainText = spannify(kt);
    }

    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView()");
        setRetainInstance(true);
        super.onCreate(savedInstanceState);

        return inflater.inflate(R.layout.fragment_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        this.view = view;
        setView();
    }

    private void setView() {
        if (view == null) {
            Log.w(TAG, "Not setting view in SettingFragment because view is null");
            return;
        }

        TextView stTxtView = view.findViewById(R.id.settingsFileTextView);
        stTxtView.setText(settingsText);

        TextView kcTxtView = view.findViewById(R.id.keychainFileTextView);
        kcTxtView.setText(keychainText);
    }

    private SpannableString spannify(String text) {
        // if we find a comment, make it gray and italic
        // if we find K = V, make K bold and blue
        // if we find [S] make S bold and purple
        SpannableString ss = new SpannableString(text);
        String lineSep = System.getProperty("line.separator", "\n");
        int startIdx = 0;
        while (startIdx < text.length()) {
            int idx = text.indexOf(lineSep, startIdx);
            if (idx < 0) idx = text.length() + 1;
            String line = text.substring(startIdx, idx);
            if (line.startsWith("Error ")) {
                ss.setSpan(new ForegroundColorSpan(Utils.GetThemeColor(getContext(), R.attr.colorError)), startIdx, idx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("#") || line.startsWith("//")) {
                ss.setSpan(new ForegroundColorSpan(Utils.GetThemeColor(getContext(), R.attr.colorPending)), startIdx, idx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new StyleSpan(Typeface.ITALIC), startIdx, idx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("[")) {
                ss.setSpan(new ForegroundColorSpan(Utils.GetThemeColor(getContext(), R.attr.colorOn)), startIdx, idx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new StyleSpan(Typeface.BOLD), startIdx, idx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                int eqIdx = line.indexOf("=");
                if (eqIdx > 0) {
                    ss.setSpan(new ForegroundColorSpan(Utils.GetThemeColor(getContext(), R.attr.colorInit)), startIdx, startIdx + eqIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            startIdx = idx + 1;
        }
        return ss;
    }
}