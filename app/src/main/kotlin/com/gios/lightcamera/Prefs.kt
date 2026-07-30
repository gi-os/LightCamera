package com.gios.lightcamera

import android.content.Context
import com.gios.lightcamera.camera.AfMode
import com.gios.lightcamera.camera.FlashMode
import com.gios.lightcamera.camera.FrameAspect
import com.gios.lightcamera.camera.PuriArt
import com.gios.lightcamera.filter.FaceTune
import com.gios.lightcamera.camera.PuriStrip
import com.gios.lightcamera.media.RollScope
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the viewfinder draws over the image, beyond focus and faces.
 *
 * Deliberately short. The stock Light camera puts nothing on the picture at all, and an
 * unobstructed viewfinder turned out to be the single biggest improvement to this app — so
 * the only thing on offer here is a grid, for anyone who wants one.
 */
enum class Chrome(val label: String) {
    /** Focus, faces and the level. Nothing else. */
    Clean("Clean"),

    /** Rule-of-thirds lines. */
    Thirds("Thirds"),
}

/**
 * When to lift LightOS's greyscale.
 *
 * See `ui/ColorMode.kt`. [Viewfinder] is the default because a camera showing you a grey
 * version of the colour photograph it is about to save is misrepresenting the picture — and
 * because half the filters in this app are about colour.
 */
enum class Colour(val label: String) {
    /** Leave the phone as Light set it. */
    Off("Off"),

    /** Colour while the viewfinder or a photograph is on screen; grey everywhere else. */
    Viewfinder("Viewfinder"),

    /** Colour for the whole app, the roll included. */
    Always("Whole app"),
}

/**
 * What the camera is set to, in the stock app's own terms.
 *
 * The three the Light camera offers, and the same three here. [Selfie] is not a separate
 * pipeline — it is the front lens — but the stock app presents it as a mode, and being a mode is
 * what makes it reachable without a hidden gesture.
 */
enum class CaptureMode(val label: String) {
    Photo("Camera"),
    Video("Video"),
    Selfie("Selfie"),
    ;

    /** What the mode slot in the band reads. */
    val bandLabel: String
        get() = when (this) {
            Photo -> "PHOTO"
            Video -> "VIDEO"
            Selfie -> "SELFIE"
        }
}

/**
 * How big a photograph is, which on this phone is the same question as how fast the shutter is.
 *
 * Reading out and encoding a 50MP frame is most of a second of the ISP's time; each step down is
 * roughly a halving. [Screen] is a different thing altogether — see [CameraEngine.previewFrame].
 */
enum class PhotoSize(val label: String, val longEdge: Int) {
    /** Everything the sensor has. Slowest by a wide margin. */
    Full("50MP", 8160),

    /** Four times the largest print you'd make from a phone. The default. */
    Large("12MP", 4000),

    Medium("5MP", 2560),

    Small("2MP", 1600),

    /**
     * The frame off the viewfinder, at panel resolution. No sensor capture at all, so it is as
     * instant as the app can be — and with a filter on, it is the very frame you were looking at.
     */
    Screen("Screen", 0),
    ;

    val isPreviewGrab: Boolean get() = this == Screen
}

/**
 * Which date back. Three real ones, each with its own era, order and typography.
 *
 * They are not skins on one drawing: the dot matrix is lamps behind a mask, the quartz is seven
 * segments, and the camcorder stamp is an actual typeface with an outline. Drawing all three the
 * same way is what makes fake ones look fake.
 */
enum class StampStyle(val label: String) {
    /** Amber-green dot matrix, leaning. `11  5 '21`. The compact-camera one. */
    Dots("Dots"),

    /** Orange-red seven segment, leaning. `'99 12 29`. The film SLR quartz back. */
    Quartz("Quartz"),

    /** Solid orange with a black outline, upright. `08/31/2015`. Camcorders and dashcams. */
    Outline("Camcorder"),
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

    /**
     * Deliberately not persisted. A camera should open in the mode you take photographs in;
     * finding it still in video a day later, with the shutter recording instead of shooting, is
     * a photograph missed.
     */
    private val _mode = MutableStateFlow(CaptureMode.Photo)
    val mode: StateFlow<CaptureMode> = _mode.asStateFlow()

    private val _aspect = MutableStateFlow(FrameAspect.byLabel(prefs.getString(ASPECT, null)))
    val aspect: StateFlow<FrameAspect> = _aspect.asStateFlow()

    private val _photoSize = MutableStateFlow(
        PhotoSize.entries.firstOrNull { it.name == prefs.getString(SIZE, null) } ?: PhotoSize.Large,
    )
    val photoSize: StateFlow<PhotoSize> = _photoSize.asStateFlow()

    private val _chrome = MutableStateFlow(
        Chrome.entries.firstOrNull { it.name == prefs.getString(CHROME, null) } ?: Chrome.Clean,
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

    /**
     * The roll shows **everything** by default.
     *
     * Narrowing it to `DCIM` is technically the definition of a camera roll, but in practice
     * that hides screenshots, saved pictures and anything a messaging app wrote elsewhere —
     * so the roll appeared to be missing photographs that are plainly on the phone. All
     * images, with a toggle in the header for anyone who wants only their own.
     */
    private val _scope = MutableStateFlow(
        RollScope.entries.firstOrNull { it.name == prefs.getString(SCOPE, null) }
            ?: RollScope.Everything,
    )
    val scope: StateFlow<RollScope> = _scope.asStateFlow()

    /**
     * Where the viewer's send button goes, if anywhere.
     *
     * Off by default and genuinely disabled rather than hidden, because a share sheet is the one
     * place a Light Phone stops feeling like a Light Phone: a grid of every app that ever
     * registered for an image, on a phone whose whole argument is that there aren't any. Turned
     * on, it has exactly one destination and no chooser.
     */
    private val _sendToLightChat = MutableStateFlow(prefs.getBoolean(SEND_LIGHTCHAT, false))
    val sendToLightChat: StateFlow<Boolean> = _sendToLightChat.asStateFlow()

    /**
     * The date back, per kind of photograph. Off by default — it writes on the photograph and there
     * is no undo.
     *
     * **Three switches, not one, because a date back suits one kind of picture and ruins another.**
     * An amber dot-matrix date in the corner of a plain photograph is the look. The same date over a
     * Game Boy frame is two incompatible resolutions arguing: the stamp is drawn at full pixel
     * precision over an image quantised to 160 cells, so it reads as a caption pasted on rather than
     * something the camera did. So the coarse filters get their own switch and it starts off, and the
     * plain photograph and the ordinary filters get one each.
     *
     * The old single `dateStamp` key migrates into plain and filtered, leaving coarse off — nobody
     * who turned the stamp on was asking for it over a dither.
     */
    private val _stampPlain = MutableStateFlow(
        prefs.getBoolean(STAMP_PLAIN, prefs.getBoolean(DATE_STAMP, false)),
    )
    val stampPlain: StateFlow<Boolean> = _stampPlain.asStateFlow()

    private val _stampFiltered = MutableStateFlow(
        prefs.getBoolean(STAMP_FILTERED, prefs.getBoolean(DATE_STAMP, false)),
    )
    val stampFiltered: StateFlow<Boolean> = _stampFiltered.asStateFlow()

    private val _stampCoarse = MutableStateFlow(prefs.getBoolean(STAMP_COARSE, false))
    val stampCoarse: StateFlow<Boolean> = _stampCoarse.asStateFlow()

    /** Whether any of the three is on — what the style picker is worth showing for. */
    val dateStampAnywhere: Boolean
        get() = _stampPlain.value || _stampFiltered.value || _stampCoarse.value

    private val _stampStyle = MutableStateFlow(
        StampStyle.entries.firstOrNull { it.name == prefs.getString(STAMP_STYLE, null) }
            ?: StampStyle.Dots,
    )
    val stampStyle: StateFlow<StampStyle> = _stampStyle.asStateFlow()

    /**
     * Everything about a Purikura that is not the shader: the frame, the two kinds of sticker, its
     * own date, and whether the shutter takes four.
     *
     * **Random by default, and none of it written down.** A booth does not remember what you chose
     * last week; you sit down and it hands you something. So the frame and the date both start on
     * Random — which is resolved per photograph from the seed, so it changes when you shoot rather
     * than while you compose — and the stickers start on a coin flip. The menu overrules any of it for
     * the rest of the session, in memory only.
     *
     * Four-shot always starts off: a strip is something you decide to do, not something that happens
     * to you on the first photograph of the day.
     */
    private val _puriFrame = MutableStateFlow(PuriArt.RANDOM)
    val puriFrame: StateFlow<String> = _puriFrame.asStateFlow()

    private val _puriFaceStickers = MutableStateFlow(Random.nextBoolean())
    val puriFaceStickers: StateFlow<Boolean> = _puriFaceStickers.asStateFlow()

    private val _puriMarginStickers = MutableStateFlow(Random.nextBoolean())
    val puriMarginStickers: StateFlow<Boolean> = _puriMarginStickers.asStateFlow()

    private val _puriDate = MutableStateFlow(PuriArt.RANDOM)
    val puriDate: StateFlow<String> = _puriDate.asStateFlow()

    private val _puriStrip = MutableStateFlow(PuriStrip.OFF)
    val puriStrip: StateFlow<String> = _puriStrip.asStateFlow()

    /**
     * The five parts of the look, each on its own switch.
     *
     * **Not randomised, unlike the frame and the stickers.** These are what Purikura *is* rather than
     * decoration on top of it, and a filter that arrived with the eyes off half the time would look
     * broken rather than surprising. The wash, the smoothing and the eyes start on because that is the
     * effect; the chin and the slimming start off because they are the two that can look uncanny on a
     * face the detector has boxed slightly wrong.
     */
    private val _puriWash = MutableStateFlow(true)
    val puriWash: StateFlow<Boolean> = _puriWash.asStateFlow()

    private val _puriSkin = MutableStateFlow(true)
    val puriSkin: StateFlow<Boolean> = _puriSkin.asStateFlow()

    private val _puriEyes = MutableStateFlow(true)
    val puriEyes: StateFlow<Boolean> = _puriEyes.asStateFlow()

    private val _puriChin = MutableStateFlow(false)
    val puriChin: StateFlow<Boolean> = _puriChin.asStateFlow()

    private val _puriSlim = MutableStateFlow(false)
    val puriSlim: StateFlow<Boolean> = _puriSlim.asStateFlow()

    /** The five, as the shader wants them. */
    fun puriTune(turns: Int = 0): FaceTune = FaceTune.of(
        eyes = _puriEyes.value,
        chin = _puriChin.value,
        slim = _puriSlim.value,
        skin = _puriSkin.value,
        wash = _puriWash.value,
        turns = turns,
    )

    /** The digicam focus beep and the shutter tick. */
    private val _sounds = MutableStateFlow(prefs.getBoolean(SOUNDS, true))
    val sounds: StateFlow<Boolean> = _sounds.asStateFlow()

    private val _colour = MutableStateFlow(
        Colour.entries.firstOrNull { it.name == prefs.getString(COLOUR, null) } ?: Colour.Viewfinder,
    )
    val colour: StateFlow<Colour> = _colour.asStateFlow()

    /** Frames on a newly loaded roll. */
    private val _rollLength = MutableStateFlow(prefs.getInt(ROLL_LENGTH, 24))
    val rollLength: StateFlow<Int> = _rollLength.asStateFlow()

    /** Whether the wheel is doing anything. Off for anyone who finds it twitchy. */
    private val _wheelEnabled = MutableStateFlow(prefs.getBoolean(WHEEL, true))
    val wheelEnabled: StateFlow<Boolean> = _wheelEnabled.asStateFlow()

    fun setFilter(id: String) = set(_filterId, id) { putString(FILTER, id) }

    fun setMode(value: CaptureMode) {
        _mode.value = value
    }

    fun setAspect(value: FrameAspect) = set(_aspect, value) { putString(ASPECT, value.label) }

    fun setPhotoSize(value: PhotoSize) = set(_photoSize, value) { putString(SIZE, value.name) }

    fun setChrome(value: Chrome) = set(_chrome, value) { putString(CHROME, value.name) }

    fun setFlash(value: FlashMode) = set(_flash, value) { putString(FLASH, value.name) }

    fun setAfMode(value: AfMode) = set(_afMode, value) { putString(AF_MODE, value.name) }

    fun setFacePriority(value: Boolean) =
        set(_facePriority, value) { putBoolean(FACE_PRIORITY, value) }

    fun setTimer(value: SelfTimer) = set(_timer, value) { putString(TIMER, value.name) }

    fun setScope(value: RollScope) = set(_scope, value) { putString(SCOPE, value.name) }

    fun setRollLength(value: Int) = set(_rollLength, value) { putInt(ROLL_LENGTH, value) }

    fun setWheelEnabled(value: Boolean) = set(_wheelEnabled, value) { putBoolean(WHEEL, value) }

    fun setSounds(value: Boolean) = set(_sounds, value) { putBoolean(SOUNDS, value) }

    // Nothing here writes to disk. See the note above: a booth does not remember.
    fun setPuriFrame(value: String) { _puriFrame.value = value }

    fun setPuriFaceStickers(value: Boolean) { _puriFaceStickers.value = value }

    fun setPuriMarginStickers(value: Boolean) { _puriMarginStickers.value = value }

    fun setPuriDate(value: String) { _puriDate.value = value }

    fun setPuriStrip(value: String) { _puriStrip.value = value }

    fun setPuriWash(value: Boolean) { _puriWash.value = value }

    fun setPuriSkin(value: Boolean) { _puriSkin.value = value }

    fun setPuriEyes(value: Boolean) { _puriEyes.value = value }

    fun setPuriChin(value: Boolean) { _puriChin.value = value }

    fun setPuriSlim(value: Boolean) { _puriSlim.value = value }

    fun setStampPlain(value: Boolean) = set(_stampPlain, value) { putBoolean(STAMP_PLAIN, value) }

    fun setStampFiltered(value: Boolean) =
        set(_stampFiltered, value) { putBoolean(STAMP_FILTERED, value) }

    fun setStampCoarse(value: Boolean) = set(_stampCoarse, value) { putBoolean(STAMP_COARSE, value) }

    fun setStampStyle(value: StampStyle) =
        set(_stampStyle, value) { putString(STAMP_STYLE, value.name) }

    fun setColour(value: Colour) = set(_colour, value) { putString(COLOUR, value.name) }

    fun setSendToLightChat(value: Boolean) =
        set(_sendToLightChat, value) { putBoolean(SEND_LIGHTCHAT, value) }

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
        const val SIZE = "photoSize"
        const val CHROME = "chrome"
        const val FLASH = "flash"
        const val AF_MODE = "afMode"
        const val FACE_PRIORITY = "facePriority"
        const val TIMER = "timer"
        const val SCOPE = "scope"
        const val ROLL_LENGTH = "rollLength"
        const val WHEEL = "wheel"
        const val SOUNDS = "sounds"
        /** Kept only so an existing setting can be read forward once. */
        const val DATE_STAMP = "dateStamp"
        const val STAMP_PLAIN = "stampPlain"
        const val STAMP_FILTERED = "stampFiltered"
        const val STAMP_COARSE = "stampCoarse"
        const val STAMP_STYLE = "stampStyle"
        const val COLOUR = "colour"
        const val SEND_LIGHTCHAT = "sendLightChat"
    }
}
