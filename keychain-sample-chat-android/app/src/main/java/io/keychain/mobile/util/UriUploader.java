package io.keychain.mobile.util;

import java.util.List;

import io.keychain.core.Uri;
import io.keychain.mobile.threading.TaskRunner;

public class UriUploader {
    public static void DoUpload(Uri uri, PairHelper pairHelper, TaskRunner.Callback<Boolean> callback) {
        new TaskRunner().executeAsync(() -> {
            if (uri == null) return false;

            List<Uri> serverContacts = pairHelper.getAllUri();

            boolean containsUri = false;

            for (Uri serverContact : serverContacts) {
                if (serverContact.toString().equals(uri.toString())) {
                    containsUri = true;
                    break;
                }
            }

            if (!containsUri){
                pairHelper.uploadUri(uri);
            }

            return !containsUri;
        }, callback);
    }
}
