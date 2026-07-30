package com.gios.lightcamera

import android.content.Context
import com.gios.lightcamera.camera.AfMode
import com.gios.lightcamera.camera.FlashMode
import com.gios.lightcamera.camera.FrameAspect
import com.gios.lightcamera.media.RollScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the viewfinder draws over the image. */
enum class Chrome(val label: String) {
    /** Nothing but the readouts. */
    Clean("Clean"),

    /** Sprocket edges, frame lines, a mechanical counter. */
    Film("Film"),

    /** Rule-of-thirds lines. */
    Thirds("Thirds"),
}

/** Seconds before the shutter fires. */
enum class SelfTimer(val seconds: Int, val label: String) {
    Off(0, "Off"),
    Three(3, "3s"),
    Ten(10, "10s"),
}

/**
 * Settings, as flows.
 *
 * `SharedPreferences` rather than DataStore: every value here is read on the way into a
 * frame — the filter, the aspect, whether a roll is loaded — and the shutter cannot wait on
 * a coroutine to find out what it is supposed to be doing. Synchronous reads at startup,
 * flows for the UI, and writes that are fire-and-forget.
 */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("camera", Context.MODE_PRIVATE)

    private val _filterId = MutableStateFlow(prefs.getString(FILTER, "film") ?: "film")
    val filterId: StateFlow<String> = _filterId.asStateFlow()

    private val _aspect = MutableStateFlow(FrameAspect.byLabel(prefs.getString(ASPECT, null)))
    val aspect: StateFlow<FrameAspect> = _aspect.asStateFlow()

    private val _chrome = MutableStateFlow(
        Chrome.entries.firstOrNull { it.name == prefs.getString(CHROME, null) } ?: Chrome.Film,
    )
    val chrome: StateFlow<Chrome> = _chrome.asStateFlow()

    private val _flash = MutableStateFlow(
        FlashMode.entries.firstOrNull { it.name == prefs.getString(FLASH, null) } ?: FlashMode.Off,
    )
    val flash: StateFlow<FlashMode> = _flash.asStateFlow()

    private val _afMode = MutableStateFlow(
        AfMode.entries.firstOrNull { it.name == prefs.getString(AF_MODE, null) } ?: AfMode.Single,
    )
    val afMode: StateFlow<AfMode> = _afMode.asStateFlow()

    private val _facePriority = MutableStateFlow(prefs.getBoolean(FACE_PRIORITY, true))
    val facePriority: StateFlow<Boolean> = _facePriority.asStateFlow()

    private val _timer = MutableStateFlow(
        SelfTimer.entries.firstOrNull { it.name == prefs.getString(TIMER, null) } ?: SelfTimer.Off,
    )
    val timer: StateFlow<SelfTimer> = _timer.asStateFlow()

    private val _scope = MutableStateFlow(
        RollScope.entries.firstOrNull { it.name == prefs.getString(SCOPE, null) } ?: RollScope.Camera,
    )
    val scope: StateFlow<RollScope> = _scope.asStateFlow()

    /** Frames on a newly loaded roll. */
    private val _rollLength = MutableStateFlow(prefs.getInt(ROLL_LENGTH, 24))
    val rollLength: StateFlow<Int> = _rollLength.asStateFlow()

    /** Whether the wheel is doing anything. Off for anyone who finds it twitchy. */
    private val _wheelEnabled = MutableStateFlow(prefs.getBoolean(WHEEL, true))
    val wheelEnabled: StateFlow<Boolean> = _wheelEnabled.asStateFlow()

    fun setFilter(id: String) = set(_filterId, id) { putString(FILTER, id) }

    fun setAspect(value: FrameAspect) = set(_aspect, value) { putString(ASPECT, value.label) }

    fun setChrome(value: Chrome) = set(_chrome, value) { putString(CHROME, value.name) }

    fun setFlash(value: FlashMode) = set(_flash, value) { putString(FLASH, value.name) }

    fun setAfMode(value: AfMode) = set(_afMode, value) { putString(AF_MODE, value.name) }

    fun setFacePriority(value: Boolean) =
        set(_facePriority, value) { putBoolean(FACE_PRIORITY, value) }

    fun setTimer(value: SelfTimer) = set(_timer, value) { putString(TIMER, value.name) }

    fun setScope(value: RollScope) = set(_scope, value) { putString(SCOPE, value.name) }

    fun setRollLength(value: Int) = set(_rollLength, value) { putInt(ROLL_LENGTH, value) }

    fun setWheelEnabled(value: Boolean) = set(_wheelEnabled, value) { putBoolean(WHEEL, value) }

    private fun <T> set(
        flow: MutableStateFlow<T>,
        value: T,
        write: android.content.SharedPreferences.Editor.() -> Unit,
    ) {
        flow.value = value
        prefs.edit().apply(write).apply()
    }

    private companion object {
        const val FILTER = "filter"
        const val ASPECT = "aspect"
        const val CHROME = "chrome"
        const val FLASH = "flash"
        const val AF_MODE = "afMode"
        const val FACE_PRIORITY = "facePriority"
        const val TIMER = "timer"
        const val SCOPE = "scope"
        const val ROLL_LENGTH = "rollLength"
        const val WHEEL = "wheel"
    }
}
