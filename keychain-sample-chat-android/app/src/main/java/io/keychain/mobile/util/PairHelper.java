package io.keychain.mobile.util;

import android.util.Log;

import java.util.List;

import io.keychain.core.Uri;

/**
 * A sample utility for pairing URIs from a trusted directory.
 * Only supports HTTP and a particular trusted directory endpoint (hardcoded), as well as methods.
 * However, this should get users started on the idea.
 * <p>
 * It can be used to get or upload URIs to the trusted directory under a specific "domain" (endpoint)
 */
public class PairHelper {
    private final String TAG;

    private final String domain;
    private final io.keychain.util.PairHelper localPairHelper;

    public PairHelper(io.keychain.core.Context context, String domain) {
        this.domain = domain;
        this.TAG = "PairHelper " + domain;
        this.localPairHelper = new io.keychain.util.PairHelper(context, domain);
    }

    public String getDomain() {
        return this.domain;
    }

    public void uploadUri(Uri paramUri) {
        Log.i(TAG, "Uploading " + paramUri.toString());
        localPairHelper.uploadUri(paramUri);
    }

    public List<Uri> getAllUri() {
        Log.i(TAG, "Retrieving all URIs");
        return List.of(localPairHelper.getAllUri());
    }

    public void clearAllUri() {
        Log.i(TAG, "Clearing all URIs on TD server");
        localPairHelper.clearAllUri();
    }
}