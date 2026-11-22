/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Modified for wispy-morphe-tweaks: made independent of the Piko extension.
 */

package app.wispy.extension.instagram.feedwhitelist;

import android.app.Activity;

import java.util.List;
import java.util.Map;

/**
 * Reads Instagram's obfuscated objects. Every method here is a stub whose body is replaced at
 * patch time, and returns null when given an object of another type.
 */
@SuppressWarnings("unused")
final class Instagram {

    private Instagram() {
    }

    /** Media of a home feed item. */
    static Object feedItemMedia(Object item) {
        return null;
    }

    /** User who posted a media. */
    static Object mediaUser(Object media) {
        return null;
    }

    /** User who owns a story tray reel. */
    static Object reelUser(Object reel) {
        return null;
    }

    static String userId(Object user) {
        return null;
    }

    static String username(Object user) {
        return null;
    }

    static String fullName(Object user) {
        return null;
    }

    static String profilePictureUrl(Object user) {
        return null;
    }

    /** Friendship of the signed-in user with a user, with keys such as "following". */
    static Map<?, ?> friendshipStatus(Object user) {
        return null;
    }

    /** Session of an Instagram activity. */
    static Object activitySession(Activity activity) {
        return null;
    }

    /** Id of the signed-in user, or null if the session is not a signed-in one. */
    static String sessionUserId(Object session) {
        return null;
    }

    /** Builds Instagram's user list request and runs it inline. */
    static void executeFollowingRequest(
            Object session, String path, String maxId, FollowingRequestCallback callback) {
    }

    static List<?> responseUsers(Object response) {
        return null;
    }

    static String responseNextMaxId(Object response) {
        return null;
    }
}
