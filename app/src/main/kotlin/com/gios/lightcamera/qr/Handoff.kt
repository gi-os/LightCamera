package com.gios.lightcamera.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * The two things a scanned code can do off this phone: be opened, or be copied.
 *
 * Deliberately thin. Everything that decides *what* a payload is lives in [Codes], which has no
 * Android in it and is tested; this file is only the platform call, and the only judgement it makes
 * is that a failure to resolve is reported rather than crashing the viewfinder.
 */
object CodeHandoff {

    private const val TAG = "CodeHandoff"

    /**
     * Hand the payload to whatever the phone has for it.
     *
     * `FLAG_ACTIVITY_NEW_TASK` because this is launched from the application context — the view
     * model has no activity, and without the flag the platform refuses the start outright.
     *
     * `resolveActivity` is not consulted first, on purpose: from Android 11 it answers null for
     * anything outside the manifest's `<queries>`, so checking would report "nothing opens that" on
     * a phone that opens it perfectly well. Try it and catch the refusal instead — the failure is
     * the same either way and this version has no false negatives.
     */
    fun open(context: Context, target: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }
            .onFailure { Log.w(TAG, "no handler for $target", it) }
            .getOrDefault(false)
    }

    /** The payload on the clipboard, verbatim — never the completed URL. */
    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("QR code", text)) }
    }
}
