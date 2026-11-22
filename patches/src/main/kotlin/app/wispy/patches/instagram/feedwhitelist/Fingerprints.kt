/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Modified for wispy-morphe-tweaks: made independent of the other Piko patches.
 */

package app.wispy.patches.instagram.feedwhitelist

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.instanceOf
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal const val IG_FRAGMENT_ACTIVITY_CLASS = "Lcom/instagram/base/activity/IgFragmentActivity;"
internal const val USER_SESSION_CLASS = "Lcom/instagram/common/session/UserSession;"
internal const val USER_CLASS = "Lcom/instagram/user/model/User;"
internal const val MEDIA_CLASS = "Lcom/instagram/feed/media/Media;"
internal const val REEL_RESPONSE_ITEM_CLASS = "Lcom/instagram/model/reels/ReelResponseItem;"
internal const val IMAGE_URL_CLASS = "Lcom/instagram/common/typedurl/ImageUrl;"
internal const val FRIENDSHIP_STATUS_CLASS = "Lcom/instagram/api/schemas/RelationshipInfoDict;"

/**
 * Home feed response parser, which names the home feed item type.
 */
internal object MainFeedResponseParseFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    strings = listOf("pagination_source", "is_initial_split_head_load_request", "more_available", "next_max_id"),
    filters = listOf(
        string("feed_items"),
        // Parses one feed item.
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L")),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = "Ljava/util/List;"),
    ),
)

/**
 * Adds an item to a list store. The home feed store, and so its cached and fetched posts, goes
 * through here.
 */
internal object ListStoreInsertFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L", "Ljava/lang/Object;", "I", "Z"),
    filters = listOf(
        // Item key.
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("Ljava/lang/Object;"), returnType = "Ljava/lang/String;"),
        methodCall(smali = "Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;"),
        methodCall(smali = "Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        methodCall(smali = "Ljava/util/AbstractList;->add(ILjava/lang/Object;)V"),
    ),
    custom = { method, _ -> method.parameterTypes.first() == method.definingClass },
)

/**
 * Home feed adapter models, which read the home feed store.
 */
internal object MainFeedStoreFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("MainfeedAdapter.buildModels"),
        string("updateHelper"),
        opcode(Opcode.CHECK_CAST),
    ),
)

/**
 * Adds decoded reels to the home story tray.
 */
internal object PopulateStoryTrayFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "L", "L", "L", USER_CLASS, "Ljava/lang/String;",
        "Ljava/util/List;", "Ljava/util/List;", "Z", "Z",
    ),
    filters = listOf(string("ReelStore.maybeAddNewTrayReelResponseItemIntfs")),
)

/**
 * Builds the generic user list request also used for the Following list.
 */
internal object UserListRequestFingerprint : Fingerprint(
    parameters = listOf(
        USER_SESSION_CLASS,
        "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
        "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
        "I", "Z", "Z", "Z", "Z",
    ),
    strings = listOf("search_surface", "max_id", "rank_token", "filter_dismissed_users", "enable_groups"),
    filters = listOf(
        fieldAccess(opcode = Opcode.SGET_OBJECT),
        // Response parser.
        fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L", "L", USER_SESSION_CLASS),
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * Profile request that is run synchronously: sets a callback, then runs the request inline.
 */
internal object InlineProfileRequestFingerprint : Fingerprint(
    parameters = listOf(USER_SESSION_CLASS, "Ljava/lang/String;"),
    returnType = USER_CLASS,
    filters = listOf(
        string("users/%s/info/"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("L"), returnType = "V"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L"),
            returnType = "V",
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * Request completion, which hands the response to the callback's success method.
 */
internal fun requestFinishFingerprint(requestClass: String, callbackClass: String) = Fingerprint(
    definingClass = requestClass,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("onFinish"),
        string("onSuccess"),
        methodCall(
            definingClass = callbackClass,
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "V",
        ),
    ),
)

/**
 * Next page cursor getter of the Pando user list response.
 */
internal fun userListNextMaxIdFingerprint(responseInterface: String) = Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(literal("next_max_id".hashCode())),
    custom = { _, classDef -> responseInterface in classDef.interfaces },
)

/**
 * Session getter of Instagram activities.
 */
internal object ModalActivitySessionFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/modal/ModalActivity;",
    name = "onDestroy",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = IG_FRAGMENT_ACTIVITY_CLASS,
            parameters = listOf(),
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        instanceOf(USER_SESSION_CLASS, location = MatchAfterImmediately()),
    ),
)

internal object IgFragmentActivityOnCreateFingerprint : Fingerprint(
    definingClass = IG_FRAGMENT_ACTIVITY_CLASS,
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
)

internal object IgFragmentActivityOnResumeFingerprint : Fingerprint(
    definingClass = IG_FRAGMENT_ACTIVITY_CLASS,
    name = "onResume",
    parameters = listOf(),
    returnType = "V",
)

// The user, media and friendship getters below are obfuscated, so they are found by what they read.

internal object UserIdFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    returnType = "Lcom/facebook/pando/TreeUpdaterJNI;",
    filters = listOf(
        string("strong_id__"),
        methodCall(
            definingClass = "this",
            parameters = listOf(),
            returnType = "Ljava/lang/String;",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * The username getter decodes its JSON key from a byte blob at runtime, so it is matched by the field id.
 */
internal object UsernameFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        literal(-265713450),
        methodCall(name = "getOptionalStringValueNative", location = MatchAfterWithin(1)),
    ),
)

internal object FullNameFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(string("full_name")),
)

internal object ProfilePictureFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    returnType = IMAGE_URL_CLASS,
    parameters = listOf(),
    filters = listOf(string("profile_pic_url")),
)

internal object FriendshipStatusFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    parameters = listOf(),
    returnType = FRIENDSHIP_STATUS_CLASS,
)

/**
 * Turns a friendship status into a map with keys such as "following".
 */
internal object FriendshipStatusMapperFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    parameters = listOf(FRIENDSHIP_STATUS_CLASS),
    strings = listOf("blocking", "followed_by", "following", "incoming_request"),
)

internal object MediaUserFingerprint : Fingerprint(
    definingClass = MEDIA_CLASS,
    returnType = USER_CLASS,
    parameters = listOf(),
    strings = listOf("user"),
)
