package app.wispy.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

object Constants {
    val COMPATIBILITY_INSTAGRAM = Compatibility(
        name = "Instagram",
        packageName = "com.instagram.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFC483C,
        targets = listOf(
            AppTarget(
                version = "452.1.0.53.64",
                versionCodes = mapOf(SupportedAbi.ARM64_V8A to 385811006),
            ),
        ),
    )
}
