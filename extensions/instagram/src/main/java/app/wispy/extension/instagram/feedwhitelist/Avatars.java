/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Modified for wispy-morphe-tweaks: made independent of the Piko extension.
 */

package app.wispy.extension.instagram.feedwhitelist;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads and caches profile pictures for the account rows. */
final class Avatars {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(3);
    /** Main thread only: the picture each recycled avatar view currently shows. */
    private static final Map<ImageView, String> BOUND = new WeakHashMap<>();

    private Avatars() {
    }

    static void load(ImageView view, String url) {
        // Rows are recycled, so a finished download only lands on a view still showing its account.
        BOUND.put(view, url);
        Bitmap cached = url == null ? null : CACHE.get(url);
        view.setImageBitmap(cached);
        if (url == null || cached != null) return;

        LOADER.execute(() -> {
            Bitmap bitmap = download(url);
            if (bitmap == null) return;
            CACHE.put(url, bitmap);
            view.post(() -> {
                if (url.equals(BOUND.get(view))) view.setImageBitmap(bitmap);
            });
        });
    }

    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
