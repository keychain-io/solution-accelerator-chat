package io.keychain.mobile;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.keychain.mobile.viewmodel.KeychainViewModel;

/**
 * Base activity for Keychain apps.
 * <p>
 * This activity forces a KeychainViewModel to be used, which may be overkill, but is one way to guarantee that
 * KeychainViewModel#startListeners() and KeychainViewModel#stopListeners() is used in #onResume and #onPause.
 * Failure to do that can cause issues with the repositories.
 * Since ViewModels can't tell Activities where to use their methods, one way around this is
 *   a. BaseActivity declares a KeychainViewModel
 *   b. BaseActivity uses the listener methods in its #onResume and #onPause
 *   c. BaseActivity declares @CallSuper annotation on those lifecycle methods so all classes that inherit from it must obey
 *
 */
public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    protected static final String EXTRAS_URI = "URI";
    protected static final String FIRST_EXECUTION = "FIRST_EXEC";

    // for subclasses
    protected AppCompatActivity thisContext;
    protected KeychainViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        this.thisContext = this;
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        if (preferences.getBoolean(FIRST_EXECUTION, true)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(FIRST_EXECUTION, false);
        }
    }

    @CallSuper
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        if (viewModel != null) {
            viewModel.startListeners();
        }
    }

    @CallSuper
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        if (viewModel != null) {
            viewModel.stopListeners();
        }
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
