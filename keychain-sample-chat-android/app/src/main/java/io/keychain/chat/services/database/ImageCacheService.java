package io.keychain.chat.services.database;

import android.media.Image;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ImageCacheService {

    // Stores the Image components with URL string as key
    private static Map<String, Image> imageCache = new HashMap<>();

    /// Return image for given key. Nil means image doesn't exist in cache
    static Optional<Image> getImage(String key) {

        if (imageCache.containsKey(key)) {
            return Optional.of(imageCache.get(key));
        }

        return Optional.empty();
    }

    /// Stores the image component in cache with given key
    static void setImage(String key, Image image) {
        imageCache.put(key, image);
    }
}
