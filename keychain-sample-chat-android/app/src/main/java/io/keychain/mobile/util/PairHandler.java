package io.keychain.mobile.util;

import static io.keychain.common.Constants.MSG_TYPE;

import android.util.Log;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PairHandler {
    public static class PairResult {
        private final String uri;
        private final String error;

        public PairResult(String u, String e) {
            this.uri = u;
            this.error = e;
        }
        public String getUri() { return uri; }
        public String getError() { return error; }
        public boolean isSuccess() { return error == null; }
    }
    public interface PairHandlerCallback {
        PairResult callback(JSONObject json);
    }

    private static final String TAG = "PairHandler";
    private final Map<String, PairHandlerCallback> callbackMap = new HashMap<>();

    public boolean removeCallback(String key) {
        final boolean removed = callbackMap.containsKey(key);
        callbackMap.remove(key);
        return removed;
    }

    public boolean addCallback(String key, PairHandlerCallback callback, boolean overwrite) {
        if (callback == null || (callbackMap.containsKey(key) && !overwrite)) return false;
        callbackMap.put(key, callback);
        return true;
    }

    public PairResult handlePairMessage(JSONObject json) {
        try {
            final String mtype = json.getString(MSG_TYPE);
            final PairHandlerCallback callback = callbackMap.get(mtype);
            if (callback == null) {
                Log.w(TAG, "No handler for message type: " + mtype);
                return new PairResult("-", "No handler for message type: " + mtype);
            }
            return callback.callback(json);
        } catch (Exception e) {
            Log.e(TAG, "Exception handling message: " + e.getMessage());
            return new PairResult("-", e.getMessage());
        }
    }
}
