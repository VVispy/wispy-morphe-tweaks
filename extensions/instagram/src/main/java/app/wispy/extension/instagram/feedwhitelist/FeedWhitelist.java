/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Modified for wispy-morphe-tweaks: made independent of the Piko extension.
 */

package app.wispy.extension.instagram.feedwhitelist;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Followed accounts whose home feed posts and stories are kept (whitelist) or hidden (blacklist).
 * Feed and stories are selected separately, and one with nothing selected is not filtered.
 */
@SuppressWarnings("unused")
public final class FeedWhitelist {

    static final String TAG = "WispyFeedWhitelist";

    private static final String PREFERENCES = "wispy_feed_whitelist";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_BLACKLIST = "blacklist";

    static final class Account {
        final String id;
        final String username;
        final String fullName;
        /** Signed CDN links expire, so the picture is only kept for the current list. */
        final String pictureUrl;
        final boolean feed;
        final boolean story;

        Account(String id, String username, String fullName, String pictureUrl, boolean feed, boolean story) {
            this.id = id;
            this.username = username == null ? "" : username;
            this.fullName = fullName == null || fullName.equals(username) ? "" : fullName;
            this.pictureUrl = pictureUrl;
            this.feed = feed;
            this.story = story;
        }

        Account withTargets(boolean feed, boolean story) {
            return new Account(id, username, fullName, pictureUrl, feed, story);
        }
    }

    private static volatile Context context;
    private static boolean loaded;
    private static Map<String, Account> selected = Collections.emptyMap();
    private static Set<String> feedIds = Collections.emptySet();
    private static Set<String> storyIds = Collections.emptySet();
    private static boolean blacklist;

    private FeedWhitelist() {
    }

    static void setContext(Context appContext) {
        if (context == null && appContext != null) context = appContext;
    }

    private static SharedPreferences preferences() {
        Context current = context;
        if (current == null) {
            try {
                // The feed can be filled before the first activity is created.
                current = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
                context = current;
            } catch (Throwable ignored) {
            }
        }
        return current == null ? null : current.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** Reads the stored selection once. Until there is a context nothing is filtered. */
    private static void load() {
        if (loaded) return;
        SharedPreferences preferences = preferences();
        if (preferences == null) return;
        apply(parse(preferences.getString(KEY_ACCOUNTS, "")));
        blacklist = preferences.getBoolean(KEY_BLACKLIST, false);
        loaded = true;
    }

    private static void apply(Map<String, Account> accounts) {
        Set<String> feed = new HashSet<>();
        Set<String> story = new HashSet<>();
        for (Account account : accounts.values()) {
            if (account.feed) feed.add(account.id);
            if (account.story) story.add(account.id);
        }
        selected = Collections.unmodifiableMap(accounts);
        feedIds = feed;
        storyIds = story;
    }

    /** Accounts selected for the feed or stories by id, in the order they were picked. */
    static synchronized Map<String, Account> selected() {
        load();
        return selected;
    }

    static Account get(String id) {
        return selected().get(id);
    }

    /** Saves which of the feed and stories the account is selected for. */
    static synchronized void set(Account account, boolean feed, boolean story) {
        Map<String, Account> accounts = new LinkedHashMap<>(selected());
        if (feed || story) {
            accounts.put(account.id, account.withTargets(feed, story));
        } else {
            accounts.remove(account.id);
        }
        save(accounts);
    }

    static synchronized void clear() {
        save(new LinkedHashMap<>());
    }

    static synchronized boolean isBlacklist() {
        load();
        return blacklist;
    }

    static synchronized void setBlacklist(boolean value) {
        load();
        blacklist = value;
        SharedPreferences preferences = preferences();
        if (preferences != null) preferences.edit().putBoolean(KEY_BLACKLIST, value).apply();
    }

    /** Injection point: an item about to be added to the home feed, from the network or the on-device feed cache. */
    public static boolean isAllowedInFeed(Object item) {
        try {
            Set<String> ids;
            boolean hide;
            synchronized (FeedWhitelist.class) {
                load();
                ids = feedIds;
                hide = blacklist;
            }
            if (ids.isEmpty()) return true;
            // Feed units without a post, such as the end of feed marker, are not filtered.
            Object owner = Instagram.mediaUser(Instagram.feedItemMedia(item));
            String ownerId = Instagram.userId(owner);
            if (ownerId == null) return true;
            if (ids.contains(ownerId)) return !hide;
            // Only followed accounts are filtered, so suggested posts are left to Instagram.
            return hide || !isFollowed(owner);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to read the owner of a feed item", e);
            return true;
        }
    }

    /**
     * Injection point: the reels about to be added to the home story tray.
     * The parameters follow the hooked method's registers: the signed-in user, unused, the reels.
     */
    public static List<Object> filterStoryTray(Object viewer, String unused, List<Object> reels) {
        try {
            if (reels == null || reels.isEmpty()) return reels;
            Set<String> ids;
            boolean hide;
            synchronized (FeedWhitelist.class) {
                load();
                ids = storyIds;
                hide = blacklist;
            }
            if (ids.isEmpty()) return reels;

            String viewerId = Instagram.userId(viewer);
            List<Object> filtered = new ArrayList<>(reels.size());
            for (Object reel : reels) {
                String ownerId = Instagram.userId(Instagram.reelUser(reel));
                // Your own story and reels without a single owner, such as live rooms, stay.
                if (ownerId == null || ownerId.equals(viewerId) || ids.contains(ownerId) != hide) {
                    filtered.add(reel);
                }
            }
            return filtered;
        } catch (Throwable e) {
            Log.e(TAG, "Failed to filter the story tray", e);
            return reels;
        }
    }

    /** An unknown friendship counts as followed, so whitelisted feeds don't leak followed posts. */
    private static boolean isFollowed(Object user) {
        Map<?, ?> friendship = Instagram.friendshipStatus(user);
        return friendship == null || !Boolean.FALSE.equals(friendship.get("following"));
    }

    private static Map<String, Account> parse(String value) {
        Map<String, Account> accounts = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) return accounts;
        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                String id = entry.optString("id");
                if (id.isEmpty()) continue;
                accounts.put(id, new Account(id, entry.optString("username"), entry.optString("full_name"),
                        null, entry.optBoolean("feed", true), entry.optBoolean("story", false)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read the feed whitelist", e);
        }
        return accounts;
    }

    private static void save(Map<String, Account> accounts) {
        String value = "";
        try {
            if (!accounts.isEmpty()) {
                JSONArray array = new JSONArray();
                for (Account account : accounts.values()) {
                    array.put(new JSONObject()
                            .put("id", account.id)
                            .put("username", account.username)
                            .put("full_name", account.fullName)
                            .put("feed", account.feed)
                            .put("story", account.story));
                }
                value = array.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save the feed whitelist", e);
            return;
        }
        apply(accounts);
        SharedPreferences preferences = preferences();
        if (preferences != null) preferences.edit().putString(KEY_ACCOUNTS, value).apply();
    }
}
