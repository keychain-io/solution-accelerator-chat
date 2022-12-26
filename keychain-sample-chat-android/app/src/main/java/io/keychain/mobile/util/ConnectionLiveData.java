package io.keychain.mobile.util;

import static android.content.Context.CONNECTIVITY_SERVICE;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.lifecycle.LiveData;

public class ConnectionLiveData extends LiveData<Integer> {
    private static final String TAG = "ConnectionLiveData";

    private Activity context;

    public ConnectionLiveData(Activity context) {
        super();
        this.context = context;
    }

    private BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            // disabled = -1, enabled but not connecting = 0, connected/connecting = 1
            int value = 0;
            if (ni == null || !ni.isConnectedOrConnecting()) value = -1;
            else value = 1;
            postValue(value);
        }
    };

    @Override
    protected void onActive() {
        super.onActive();
        context.registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        try {
            context.unregisterReceiver(networkReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
        }
    }
}
