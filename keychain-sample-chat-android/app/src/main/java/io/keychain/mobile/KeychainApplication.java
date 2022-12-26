package io.keychain.mobile;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.CallSuper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import io.keychain.chat.services.GatewayService;

/**
 * Use KeychainApplication, which listens to and tracks Activity lifecycle changes.
 * This way you can have effective Singletons (Gateway, MQTT, etc) which turn off when no other Activity is using them.
 * Creating these in the Activity itself can be problematic
 * - Gateway stop/start can lead to DB busy or other issues because the thread stop takes time, so putting it in Activity A's #pause or #stop
 * and B's #resume or #start fails often
 * - Mqtt disconnect/close/connect will never be satisfactory; either you close in #pause and open in #resume (big gap so new Activity can't be active immediately)
 * or you close in #stop and open in #start (overlapping, so now you have to hold counters to know not to disconnect in A's #stop because B's #start was called already)
 */
public class KeychainApplication extends Application {
    private static final String TAG = "KeychainApplication";
    private Properties applicationProperties;
    private static KeychainApplication INSTANCE;

    private GatewayService gatewayService;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        loadProperties();

        INSTANCE = this;
        gatewayService = new GatewayService(this);

        registerActivityLifecycleCallbacks(new AppLifecycleTracker() {
            @Override
            public void onForeground() { KeychainApplication.this.onForeground(); }

            @Override
            public void onBackground() { KeychainApplication.this.onBackground();}
        });
    }

    private void loadProperties() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(getAssets().open("application.properties"), StandardCharsets.UTF_8));

            applicationProperties = new Properties();
            applicationProperties.load(reader);
        } catch (IOException e) {
            Log.e(TAG, "IOException reading properties: " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException closing reader: " + e.getMessage());
                }
            }
        }
    }

    public String loadAssetString(String assetName) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(getAssets().open(assetName), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String lineSep = System.getProperty("line.separator", "\n");
            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                sb.append(line).append(lineSep);
            }
            return sb.toString();
        } catch(IOException e){
            Log.e(TAG, "IOException reading asset: " + e.getMessage());
        } finally{
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException closing asset reader: " + e.getMessage());
                }
            }
        }
        return null;
    }

    public String getApplicationProperty(String property) {
        return this.applicationProperties.getProperty(property);
    }

    public static KeychainApplication GetInstance() { return INSTANCE; }

    public Context getContext(){
        return INSTANCE.getApplicationContext();
    }

    public GatewayService getGatewayService() { return gatewayService; }

    @Override
    public void onTerminate() {
        Log.d(TAG, "Terminating application");
        super.onTerminate();
    }

    @CallSuper
    protected void onForeground() {
        Log.d(TAG, "Application foregrounded");
        gatewayService.startMonitor();
    }

    @CallSuper
    protected void onBackground() {
        Log.d(TAG, "Application backgrounded");
        gatewayService.stopMonitor();
    }
}