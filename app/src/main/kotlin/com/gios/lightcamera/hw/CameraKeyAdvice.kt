package com.gios.lightcamera.hw

import android.content.Context
import android.content.pm.PackageManager

/**
 * Why the camera button might be doing nothing.
 *
 * This app cannot fix a dead shutter from inside itself, and that is the point of saying so
 * plainly rather than looking broken. The camera key is dispatched to the focused window like
 * any other key — but an `AccessibilityService` with `flagRequestFilterKeyEvents` sees keys
 * *before* the focused app and can swallow them, and
 * [LightControl](https://github.com/gi-os/LightControl) binds this exact key by default. Its
 * default action is "open the camera", which it used to perform even with a camera already
 * open and in front: the key never arrived here, so the shutter was dead.
 *
 * LightControl 1.1.6 passes both stages to any app registered for `STILL_IMAGE_CAMERA`. So the
 * check is a version comparison, and the advice is one line: update it.
 *
 * Deliberately not a general "is any accessibility service filtering keys" check — that would
 * need `ENABLED_ACCESSIBILITY_SERVICES` off `Settings.Secure` and could only ever produce a
 * shrug. Naming the one app that is known to do this, and the version that stopped, is worth
 * more than a warning about accessibility services in general.
 */
object CameraKeyAdvice {

    private const val LIGHT_CONTROL = "com.gios.lightcontrol"

    /** The release that hands the camera key to cameras. */
    private val FIXED_IN = intArrayOf(1, 1, 6)

    /**
     * A sentence to show the user, or null when there is nothing to say — which is the usual
     * case, either because LightControl isn't installed or because it is new enough.
     */
    fun problem(context: Context): String? {
        val installed = versionOf(context, LIGHT_CONTROL) ?: return null
        if (atLeast(installed, FIXED_IN)) return null
        val version = installed.joinToString(".")
        return "LightControl $version binds the camera button, so this app never sees it. " +
            "Update LightControl to 1.1.6 or newer and the shutter works. Until then, either " +
            "volume key takes the photograph."
    }

    private fun versionOf(context: Context, pkg: String): IntArray? = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(pkg, 0)
        parse(info.versionName)
    }.getOrElse {
        // Not installed, or hidden from us by package visibility rules. Either way, silence.
        if (it is PackageManager.NameNotFoundException) null else null
    }

    /** `1.1.6` to `[1, 1, 6]`. Anything unparseable becomes 0, which reads as "older". */
    internal fun parse(name: String?): IntArray {
        val parts = (name ?: "").split('.', '-', ' ')
        return IntArray(3) { i -> parts.getOrNull(i)?.toIntOrNull() ?: 0 }
    }

    internal fun atLeast(version: IntArray, minimum: IntArray): Boolean {
        for (i in minimum.indices) {
            val a = version.getOrElse(i) { 0 }
            val b = minimum[i]
            if (a != b) return a > b
        }
        return true
    }
}
