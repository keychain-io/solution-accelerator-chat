package io.keychain.mobile.util;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.keychain.core.Uri;

/**
 * A sample utility for pairing URIs from a trusted directory.
 * Only supports HTTP and a particular trusted directory endpoint (hardcoded), as well as methods.
 * However, this should get users started on the idea.
 *
 * It can be used to get or upload URIs to the trusted directory under a specific "domain" (endpoint)
 */
public class PairHelper {
    private final String TAG;

    private final String domain;
    private final String baseUri;

    public PairHelper(String primaryHost, int primaryPort, String domain) {
        this.domain = domain;
        this.TAG = "PairHelper " + domain;
        this.baseUri = "http://" + primaryHost + ":" + primaryPort + "/adsimulator/";
    }

    public String getDomain() {
        return this.domain;
    }

    private URL getUrl(String subDomain, String uri) {
        String suffix = "";
        if (uri != null) {
            final String[] parts = uri.split("[;:]");
            if (parts.length != 4) {
                Log.e(TAG, "Invalid URI: " + uri);
                return null;
            }

            final String eot = parts[0];
            final String eov = parts[1];
            final String sot = parts[2];
            final String sov = parts[3];
            suffix = "/" + eot + "/" + eov + "/" + sot + "/" + sov;
        }

        try {
            return new URL(this.baseUri + subDomain + "/" + this.domain + suffix);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Error creating URL: " + e.getMessage());
        }
        return null;
    }

    private String generalHttpAction(String subDomain, String optionalUri) {
        URL url = getUrl(subDomain, optionalUri);
        if (url == null) return null;

        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (Exception e) {
            Log.e(TAG, "Error opening URL: " + e.getMessage());
            return null;
        }

        String body = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Server returned status " + urlConnection.getResponseCode());
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                body = sb.toString();
            } else {
                Log.w(TAG, "Server returned status " + urlConnection.getResponseCode());
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection failed with exception: " + e.getMessage());
        }

        urlConnection.disconnect();
        return body;
    }

    public void uploadUri(Uri paramUri) {
        Log.i(TAG, "Uploading " + paramUri.toString());
        generalHttpAction("uploaduri", paramUri.toString());
    }

    public List<Uri> getAllUri() {
        Log.i(TAG, "Retrieving all URIs");

        String result = generalHttpAction("getalluri", null);

        final List<Uri> emptyList = Collections.emptyList();

        if (result == null || result.isEmpty()) {
            // error
            Log.w(TAG, "No URIs to return");
            return emptyList;
        }

        try {
            JSONObject obj = new JSONObject(result);
            JSONArray results = obj.getJSONArray("results");
            final List<Uri> uris = new ArrayList<>(results.length());
            Log.i(TAG, "  > Retrieved " + results.length() + " URIs");

            for (int i = 0; i < results.length(); i++) {
                final JSONObject o = results.getJSONObject(i);
                final Uri contactUri = new Uri(o.getString("encr_txid") + ":" + o.getInt("encr_vout") + ";" +
                                               o.getString("sign_txid") + ":" + o.getInt("sign_vout"));
                uris.add(contactUri);
            }
            return uris;
        } catch (Exception e) {
            Log.e(TAG, "Exception parsing JSON: " + e.getMessage());
        }
        return emptyList;
    }

    public void clearAllUri() {
        Log.i(TAG, "Clearing all URIs on TD server");
        generalHttpAction("clearalluri", null);
    }
}