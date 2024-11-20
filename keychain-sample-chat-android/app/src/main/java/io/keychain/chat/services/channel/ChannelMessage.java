package io.keychain.chat.services.channel;

import static io.keychain.common.Constants.MSG_TYPE;
import static io.keychain.common.Constants.PAIR_ACK;
import static io.keychain.common.Constants.PAIR_REQUEST;
import static io.keychain.common.Constants.PAIR_RESPONSE;
import static io.keychain.common.Constants.RECEIVER_ID;
import static io.keychain.common.Constants.SENDER_ID;
import static io.keychain.common.Constants.SENDER_NAME;
import static io.keychain.common.Constants.SENDER_SUB_NAME;

import android.util.Log;

import org.json.JSONObject;

import io.keychain.core.Persona;

public class ChannelMessage {
    private static final String TAG = "ChannelMessage";
    public static final String ERROR_CREATING_PAIR_RESPONSE = "Error creating pair response: ";
    public static final String ERROR_CREATING_PAIR_REQUEST = "Error creating pair request: ";

    public static JSONObject MakePairAck(Persona me, String receiverId) {
        JSONObject resp = new JSONObject();

        try {
            resp.put(MSG_TYPE, PAIR_ACK);
            resp.put(RECEIVER_ID, receiverId);
            resp.put(SENDER_ID, me.getUri().toString());
            resp.put(SENDER_NAME, me.getName());
            resp.put(SENDER_SUB_NAME, me.getSubName());

            return resp;
        } catch (Exception e) {
            Log.e(TAG, ERROR_CREATING_PAIR_RESPONSE + e.getMessage());
        }

        return null;
    }

    public static JSONObject MakePairResponse(Persona me, String receiverId) {
        JSONObject resp = new JSONObject();

        try {
            resp.put(MSG_TYPE, PAIR_RESPONSE);
            resp.put(RECEIVER_ID, receiverId);
            resp.put(SENDER_ID, me.getUri().toString());
            resp.put(SENDER_NAME, me.getName());
            resp.put(SENDER_SUB_NAME, me.getSubName());

            return resp;
        } catch (Exception e) {
            Log.e(TAG, ERROR_CREATING_PAIR_RESPONSE + e.getMessage());
        }

        return null;
    }

    public static JSONObject MakePairRequest(Persona me, String requestUri, String overrideSubName) {
        JSONObject jobj = new JSONObject();

        try {
            jobj.put(MSG_TYPE, PAIR_REQUEST);

            if (requestUri != null) {
                jobj.put(RECEIVER_ID, requestUri);
            }

            jobj.put(SENDER_ID, me.getUri().toString());
            jobj.put(SENDER_NAME, me.getName());
            jobj.put(SENDER_SUB_NAME, overrideSubName == null ? me.getSubName() : overrideSubName);

            return jobj;
        } catch (Exception e) {
            Log.e(TAG, ERROR_CREATING_PAIR_REQUEST + e.getMessage());
        }

        return null;
    }
}
