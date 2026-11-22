/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Modified for wispy-morphe-tweaks: made independent of the Piko extension.
 */

package app.wispy.extension.instagram.feedwhitelist;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the accounts the current user follows, using Instagram's own authenticated requests. */
final class FollowingFetcher {

    interface Listener {
        void onProgress(int loaded);

        void onLoaded(List<FeedWhitelist.Account> following);

        void onError();
    }

    private static final String FOLLOWING_PATH = "friendships/%s/following/";
    private static final int MAX_PAGES = 500;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static List<FeedWhitelist.Account> cache;
    private static String cacheOwnerId;

    private FollowingFetcher() {
    }

    static void load(Activity activity, boolean refresh, Listener listener) {
        Object session = Instagram.activitySession(activity);
        String viewerId = Instagram.sessionUserId(session);
        if (viewerId == null) {
            Log.w(FeedWhitelist.TAG, "No user session for the following list");
            listener.onError();
            return;
        }
        synchronized (FollowingFetcher.class) {
            if (!refresh && cache != null && viewerId.equals(cacheOwnerId)) {
                listener.onLoaded(cache);
                return;
            }
        }

        new Thread(() -> {
            try {
                List<FeedWhitelist.Account> following = Collections.unmodifiableList(
                        fetchAll(session, viewerId, listener));
                synchronized (FollowingFetcher.class) {
                    cache = following;
                    cacheOwnerId = viewerId;
                }
                MAIN.post(() -> listener.onLoaded(following));
            } catch (Throwable e) {
                // This runs off the main thread, where anything uncaught would close the app.
                Log.e(FeedWhitelist.TAG, "Failed to load followed accounts", e);
                MAIN.post(listener::onError);
            }
        }, "wispy-following").start();
    }

    private static List<FeedWhitelist.Account> fetchAll(Object session, String viewerId, Listener listener) {
        String path = String.format(FOLLOWING_PATH, viewerId);
        Map<String, FeedWhitelist.Account> accounts = new LinkedHashMap<>();
        String maxId = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            FollowingRequestCallback callback = new FollowingRequestCallback();
            // Runs inline on this thread, so the callback has its response when this returns.
            Instagram.executeFollowingRequest(session, path, maxId, callback);
            Object response = callback.response;
            if (response == null) throw new IllegalStateException("Following request failed");

            List<?> users = Instagram.responseUsers(response);
            if (users != null) {
                for (Object user : users) {
                    String id = Instagram.userId(user);
                    if (id == null || id.isEmpty()) continue;
                    accounts.putIfAbsent(id, new FeedWhitelist.Account(id, Instagram.username(user),
                            Instagram.fullName(user), Instagram.profilePictureUrl(user), false, false));
                }
            }
            int loaded = accounts.size();
            MAIN.post(() -> listener.onProgress(loaded));

            String nextMaxId = Instagram.responseNextMaxId(response);
            if (nextMaxId == null || nextMaxId.isEmpty() || nextMaxId.equals(maxId)) break;
            maxId = nextMaxId;
        }
        return new ArrayList<>(accounts.values());
    }
}
