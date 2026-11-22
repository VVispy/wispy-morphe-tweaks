/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Modified for wispy-morphe-tweaks: made independent of the other Piko patches.
 */

package app.wispy.patches.instagram.feedwhitelist

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.wispy.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val EXTENSION_PACKAGE = "Lapp/wispy/extension/instagram/feedwhitelist"
private const val WHITELIST_CLASS = "$EXTENSION_PACKAGE/FeedWhitelist;"
private const val INSTAGRAM_CLASS = "$EXTENSION_PACKAGE/Instagram;"
private const val CALLBACK_CLASS = "$EXTENSION_PACKAGE/FollowingRequestCallback;"
private const val PICKER_CLASS = "$EXTENSION_PACKAGE/FeedWhitelistPicker;"

/** Replaces an extension stub with [smali], using as many registers as it needs. */
private fun replaceStub(name: String, registers: Int, smali: String) {
    val extension = mutableClassDefBy(INSTAGRAM_CLASS)
    val stub = extension.methods.singleOrNull { it.name == name }
        ?: throw PatchException("Missing extension stub $INSTAGRAM_CLASS->$name")
    extension.methods.remove(stub)
    extension.methods.add(
        ImmutableMethod(
            INSTAGRAM_CLASS,
            stub.name,
            stub.parameters,
            stub.returnType,
            stub.accessFlags,
            null,
            null,
            MutableMethodImplementation(registers),
        ).toMutable().apply { addInstructions(0, smali.trimIndent()) },
    )
}

private fun Method.firstReference(opcode: Opcode) =
    implementation?.instructions?.firstOrNull { it.opcode == opcode }

/** Smali reference of a getter without arguments. */
private fun getter(definingClass: String, name: String, returnType: String) = "$definingClass->$name()$returnType"

private val Method.getter get() = getter(definingClass, name, returnType)

/** Runs [read] on the first argument if it is a [receiverType], and returns null otherwise. */
private fun guarded(receiverType: String, read: String) =
    """
    instance-of v0, p0, $receiverType
    if-eqz v0, :wispy_none
    check-cast p0, $receiverType
    $read
    return-object p0
    :wispy_none
    const/4 p0, 0x0
    return-object p0
    """

/** A no-argument getter, guarded so other objects return null. */
private fun guardedGetter(receiverType: String, getter: String) =
    guarded(
        receiverType,
        """
        invoke-virtual {p0}, $getter
        move-result-object p0
        """,
    )

@Suppress("unused")
val whitelistFollowedAccountsPatch =
    patch(
        name = "Whitelist followed accounts",
        description = "Adds a menu, opened by long-pressing the Home tab, to pick followed accounts " +
            "whose home feed posts and stories are the only ones shown, or are hidden.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        extendWith("extensions/wispy-instagram.mpe")

        execute {
            // Media of a home feed item.
            val itemType = MainFeedResponseParseFingerprint.instructionMatches[1].methodReference.returnType
            val mediaGetter = classDefBy(itemType).methods.singleOrNull { method ->
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes.isEmpty() && method.returnType == MEDIA_CLASS
            } ?: throw PatchException("Could not resolve the media getter of $itemType")
            replaceStub("feedItemMedia", 2, guardedGetter(itemType, mediaGetter.getter))

            // Owner of a post and of a story tray reel.
            replaceStub("mediaUser", 2, guardedGetter(MEDIA_CLASS, MediaUserFingerprint.originalMethod.getter))
            val reelUserField = classDefBy(REEL_RESPONSE_ITEM_CLASS).fields.firstOrNull { it.type == USER_CLASS }
                ?: throw PatchException("Could not resolve the user of a story tray reel")
            replaceStub(
                "reelUser",
                2,
                guarded(
                    REEL_RESPONSE_ITEM_CLASS,
                    "iget-object p0, p0, $REEL_RESPONSE_ITEM_CLASS->${reelUserField.name}:$USER_CLASS",
                ),
            )

            // What is read from a user.
            val userIdName = UserIdFingerprint.instructionMatches.last().methodReference.name
            replaceStub("userId", 2, guardedGetter(USER_CLASS, getter(USER_CLASS, userIdName, "Ljava/lang/String;")))
            replaceStub("username", 2, guardedGetter(USER_CLASS, UsernameFingerprint.originalMethod.getter))
            replaceStub("fullName", 2, guardedGetter(USER_CLASS, FullNameFingerprint.originalMethod.getter))
            replaceStub(
                "profilePictureUrl",
                2,
                guarded(
                    USER_CLASS,
                    """
                    invoke-virtual {p0}, ${ProfilePictureFingerprint.originalMethod.getter}
                    move-result-object p0
                    if-eqz p0, :wispy_none
                    invoke-interface {p0}, $IMAGE_URL_CLASS->getUrl()Ljava/lang/String;
                    move-result-object p0
                    """,
                ),
            )
            val friendshipMapper = FriendshipStatusMapperFingerprint.originalMethod
            if (!AccessFlags.STATIC.isSet(friendshipMapper.accessFlags)) {
                throw PatchException("The friendship status mapper is not static")
            }
            replaceStub(
                "friendshipStatus",
                2,
                guarded(
                    USER_CLASS,
                    """
                    invoke-virtual {p0}, ${FriendshipStatusFingerprint.originalMethod.getter}
                    move-result-object p0
                    if-eqz p0, :wispy_none
                    invoke-static {p0}, $friendshipMapper
                    move-result-object p0
                    """,
                ),
            )

            // Filter what enters the home feed store. On launch the feed comes from the on-device
            // cache, which never passes through the network response parser.
            ListStoreInsertFingerprint.method.apply {
                val storeBase = definingClass
                val mainFeedStore = MainFeedStoreFingerprint.instructionMatches.last()
                    .getInstruction<ReferenceInstruction>().getReference<TypeReference>()!!.type
                if (generateSequence(mainFeedStore) { classDefBy(it).superclass }.none { it == storeBase }) {
                    throw PatchException("$mainFeedStore is not a $storeBase list store")
                }

                addInstructionsWithLabels(
                    0,
                    """
                    instance-of v0, p0, $mainFeedStore
                    if-eqz v0, :wispy_keep
                    invoke-static { p2 }, $WHITELIST_CLASS->isAllowedInFeed(Ljava/lang/Object;)Z
                    move-result v0
                    if-nez v0, :wispy_keep
                    return-void
                    """.trimIndent(),
                    ExternalLabel("wispy_keep", getInstruction(0)),
                )
            }

            // Home story tray, after decoding. The signed-in user is passed so their own story stays.
            PopulateStoryTrayFingerprint.method.addInstructions(
                0,
                """
                invoke-static/range { p3 .. p5 }, $WHITELIST_CLASS->filterStoryTray(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;)Ljava/util/List;
                move-result-object p5
                """.trimIndent(),
            )

            // The Following list request, its callback and the way Instagram runs a request inline.
            val userListRequest = UserListRequestFingerprint.method
            val requestType = userListRequest.returnType
            val parserType = UserListRequestFingerprint.instructionMatches[1].fieldReference.type

            val setCallback = InlineProfileRequestFingerprint.instructionMatches[1].methodReference
            val runInline = InlineProfileRequestFingerprint.instructionMatches[2].methodReference
            val callbackType = setCallback.parameterTypes.single().toString()

            val onSuccess = requestFinishFingerprint(requestType, callbackType)
                .instructionMatches.last().methodReference.name

            mutableClassDefBy(CALLBACK_CLASS).apply {
                setSuperClass(callbackType)
                (methods.singleOrNull { it.name == "onRequestSuccess" }
                    ?: throw PatchException("Missing extension callback method")).name = onSuccess
            }

            replaceStub(
                "executeFollowingRequest",
                16,
                """
                check-cast p0, $USER_SESSION_CLASS
                move-object v0, p0
                move-object v1, p1
                const/4 v2, 0x0
                const/4 v3, 0x0
                move-object v4, p2
                const/4 v5, 0x0
                const/4 v6, 0x0
                const/4 v7, 0x0
                const/4 v8, 0x0
                const/4 v9, 0x0
                const/4 v10, 0x0
                const/4 v11, 0x0
                invoke-static/range { v0 .. v11 }, $userListRequest
                move-result-object v0
                invoke-virtual { v0, p3 }, $requestType->${setCallback.name}($callbackType)V
                invoke-static { v0 }, $runInline
                return-void
                """,
            )

            // Users and next page cursor of the user list response.
            val parseMethod = classDefBy(parserType).methods.singleOrNull { it.name == "unsafeParseFromJson" }
                ?: throw PatchException("Could not resolve the user list response parser")
            val responseType = parseMethod.implementation!!.instructions
                .last { it.opcode == Opcode.NEW_INSTANCE }.getReference<TypeReference>()!!.type
            val responseClass = classDefBy(responseType)
            val itemsGetter = responseClass.methods.singleOrNull {
                it.name == "getItems" && it.parameterTypes.isEmpty()
            } ?: throw PatchException("Could not resolve the user list response items")
            val responseInterface = itemsGetter.firstReference(Opcode.CHECK_CAST)
                ?.getReference<TypeReference>()?.type
                ?: throw PatchException("Could not resolve the user list response interface")

            val nextMaxIdName = userListNextMaxIdFingerprint(responseInterface).originalMethod.name
            val nextMaxIdGetter = responseClass.methods.singleOrNull { method ->
                method.parameterTypes.isEmpty() && method.returnType == "Ljava/lang/String;" &&
                    method.implementation?.instructions?.any { instruction ->
                        instruction.getReference<MethodReference>()?.let {
                            it.definingClass == responseInterface && it.name == nextMaxIdName
                        } == true
                    } == true
            } ?: throw PatchException("Could not resolve the user list next page cursor")

            replaceStub("responseUsers", 2, guardedGetter(responseType, itemsGetter.getter))
            replaceStub("responseNextMaxId", 2, guardedGetter(responseType, nextMaxIdGetter.getter))

            // The signed-in session of an Instagram activity.
            val sessionGetter = ModalActivitySessionFingerprint.instructionMatches.first().methodReference
            replaceStub(
                "activitySession",
                2,
                guardedGetter(
                    IG_FRAGMENT_ACTIVITY_CLASS,
                    getter(IG_FRAGMENT_ACTIVITY_CLASS, sessionGetter.name, sessionGetter.returnType),
                ),
            )
            replaceStub(
                "sessionUserId",
                2,
                guardedGetter(USER_SESSION_CLASS, getter(USER_SESSION_CLASS, "getUserId", "Ljava/lang/String;")),
            )

            // The app context for the stored selection, and the Home tab that opens the menu.
            IgFragmentActivityOnCreateFingerprint.method.addInstruction(
                0,
                "invoke-static/range { p0 .. p0 }, $PICKER_CLASS->onActivityCreate(Landroid/app/Activity;)V",
            )
            IgFragmentActivityOnResumeFingerprint.method.addInstruction(
                0,
                "invoke-static/range { p0 .. p0 }, $PICKER_CLASS->onActivityResume(Landroid/app/Activity;)V",
            )
        }
    }
