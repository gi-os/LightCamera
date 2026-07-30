package com.gios.lightcamera.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lightcamera.CaptureMode
import com.gios.lightcamera.Prefs
import com.gios.lightcamera.camera.CameraEngine
import com.gios.lightcamera.camera.FaceBox
import com.gios.lightcamera.camera.Frames
import com.gios.lightcamera.filter.Filters
import com.gios.lightcamera.hw.Beeps
import com.gios.lightcamera.media.MediaStoreRepo
import com.gios.lightcamera.media.Photo
import com.gios.lightcamera.media.RollScope
import com.gios.lightcamera.media.Thumbs
import com.gios.lightcamera.roll.FilmRoll
import com.gios.lightcamera.roll.Roll
import com.gios.lightcamera.ui.theme.LightHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Everything the two screens share.
 *
 * The interesting method is [shoot], which is the only place the app's three modes meet: a
 * filter that has to be applied to the bytes, a frame shape that has to be cropped to, and
 * a roll that may or may not be loaded and therefore decides whether the photo goes into the
 * gallery at all.
 */
class CameraViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    val engine = CameraEngine(app)
    val thumbs = Thumbs(app)
    private val repo = MediaStoreRepo(app)
    val filmRoll = FilmRoll(app)
    private val beeps = Beeps(app)

    val roll: StateFlow<Roll?> get() = filmRoll.roll

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _loadingRoll = MutableStateFlow(true)
    val loadingRoll: StateFlow<Boolean> = _loadingRoll.asStateFlow()

    /** True from the moment the shutter is pressed until the file is written. */
    private val _shooting = MutableStateFlow(false)
    val shooting: StateFlow<Boolean> = _shooting.asStateFlow()

    /** Ticks once per captured frame, for the viewfinder blink. */
    private val _shutterTick = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val shutterTick: SharedFlow<Unit> = _shutterTick.asSharedFlow()

    /** Seconds left on the self timer, or null. */
    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown: StateFlow<Int?> = _countdown.asStateFlow()

    /** Short-lived lines of text for the viewfinder. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** The most recent developed roll, so the contact sheet can be shown. */
    private val _developed = MutableStateFlow<FilmRoll.DevelopedRoll?>(null)
    val developed: StateFlow<FilmRoll.DevelopedRoll?> = _developed.asStateFlow()

    /**
     * Set when another app launched us with `IMAGE_CAPTURE`. The next photo goes there and
     * the activity finishes, rather than the photo landing in the roll.
     */
    var captureRequestOutput: Uri? = null
    private val _captureRequestDone = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val captureRequestDone: SharedFlow<Boolean> = _captureRequestDone.asSharedFlow()

    private val _filter = MutableStateFlow(Filters.byId(prefs.filterId.value))
    val filter: StateFlow<Filters.Filter> = _filter.asStateFlow()

    /** Seconds into the current take, for the readout. */
    private val _recordSeconds = MutableStateFlow(0)
    val recordSeconds: StateFlow<Int> = _recordSeconds.asStateFlow()

    var audioGranted: Boolean = false

    /** While the dial is caught on None. See [Filters.NONE_DWELL_MS]. */
    private var dialHeldUntil = 0L

    private var observer: AutoCloseable? = null
    private var lastPriorityFace: FaceBox? = null

    /** Read from the face collector below, so declared above it. Ints, so harmless either way. */
    @Volatile private var viewWidth = 0

    @Volatile private var viewHeight = 0

    /**
     * **Every field this block touches must be declared above it.**
     *
     * `viewModelScope` runs on `Dispatchers.Main.immediate`, and the view model is built on the
     * main thread — so each `launch` here starts executing *synchronously, inside the
     * constructor*, and a `StateFlow` hands over its current value on subscription. A field
     * declared below this point is therefore still null when the collector first fires, and the
     * app dies in the view model's constructor with a null-pointer exception that names a
     * property Kotlin swore was non-null. That is exactly how v1.5.6 shipped an instant crash:
     * the recording collector wrote to a counter declared thirty lines further down.
     */
    init {
        viewModelScope.launch {
            prefs.filterId.collect { id -> _filter.value = Filters.byId(id) }
        }
        viewModelScope.launch {
            prefs.afMode.collect { engine.afMode = it }
        }
        viewModelScope.launch {
            prefs.facePriority.collect { engine.facePriority = it }
        }
        viewModelScope.launch {
            prefs.flash.collect { engine.setFlash(it) }
        }
        // Size is a use-case configuration, so changing it rebinds the camera.
        viewModelScope.launch {
            prefs.photoSize.collect { engine.setPhotoSize(it, prefs.flash.value) }
        }
        // Continuous AF is driven from the face list rather than from a timer, so a still
        // subject costs nothing at all.
        viewModelScope.launch {
            engine.faces.collect { faces ->
                val priority = com.gios.lightcamera.camera.FaceMapper
                    .priority(faces, viewWidth, viewHeight)
                engine.trackFaces(lastPriorityFace, priority)
                lastPriorityFace = priority
            }
        }
        viewModelScope.launch {
            prefs.scope.collect { refreshRoll() }
        }
        // Focus confirmation: two blips and a buzz, the way a compact camera does it. Fired
        // from the camera's own AF result, so it lands when the lens lands — not when a
        // request was sent.
        viewModelScope.launch {
            engine.focusOutcome.collect { locked ->
                if (locked) {
                    LightHaptics.click(getApplication<Application>())
                    if (prefs.sounds.value) beeps.focusLocked()
                } else {
                    if (prefs.sounds.value) beeps.focusFailed()
                }
            }
        }
        viewModelScope.launch {
            shutterTick.collect { if (prefs.sounds.value) beeps.shutter() }
        }
        // The elapsed counter ticks only while something is being recorded, so an idle camera
        // isn't waking up once a second to look at a clock.
        viewModelScope.launch {
            engine.recording.collect { on ->
                if (!on) {
                    _recordSeconds.value = 0
                    return@collect
                }
                val startedAt = System.currentTimeMillis()
                while (engine.recording.value) {
                    _recordSeconds.value = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                    delay(500)
                }
                _recordSeconds.value = 0
            }
        }
    }

    fun onViewSized(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        engine.onViewSized(width, height)
    }

    /* ---------------- the roll above the viewfinder ---------------- */

    fun startObservingMedia() {
        if (observer != null) return
        observer = repo.observe {
            viewModelScope.launch { refreshRoll() }
        }
        viewModelScope.launch { refreshRoll() }
    }

    suspend fun refreshRoll() {
        val loaded = repo.load(prefs.scope.value)
        _photos.value = loaded
        _loadingRoll.value = false
    }

    fun onPermissionsChanged() {
        viewModelScope.launch { refreshRoll() }
    }

    /* ---------------- filters ---------------- */

    /**
     * One notch of the wheel, or one sideways swipe.
     *
     * Every notch buzzes, whether or not it moves the dial — the wheel is a physical control and
     * silence from it reads as a control that isn't working. The buzz is also what tells you the
     * dial is *caught* on None rather than dead: notches inside the dwell window are felt and
     * discarded.
     */
    fun stepFilter(by: Int) {
        if (videoMode()) {
            showNotice("Filters are photo only")
            return
        }
        LightHaptics.advance(getApplication<Application>())
        val now = System.currentTimeMillis()
        if (now < dialHeldUntil) return
        val next = Filters.step(filter.value, by)
        prefs.setFilter(next.id)
        if (next.id == Filters.none.id) dialHeldUntil = now + Filters.NONE_DWELL_MS
        // **No name flashed on screen.** The viewfinder is already showing you the filter — a
        // label naming what you can plainly see is a label in the way of it. The buzz says the
        // dial moved; the picture says where to.
    }

    fun setFilter(id: String) {
        // Chosen deliberately from the grid, so the dial has no business holding on to it.
        dialHeldUntil = 0L
        prefs.setFilter(id)
    }

    /* ---------------- modes ---------------- */

    fun videoMode(): Boolean = prefs.mode.value == CaptureMode.Video

    fun setMode(next: CaptureMode) {
        if (engine.recording.value) {
            showNotice("Stop recording first")
            return
        }
        prefs.setMode(next)
        engine.setMode(next, prefs.flash.value)
        showNotice(next.bandLabel)
    }

    /** The lens switch, which in Photo and Selfie is the same thing as switching mode. */
    fun flipLens() {
        when (prefs.mode.value) {
            CaptureMode.Photo -> setMode(CaptureMode.Selfie)
            CaptureMode.Selfie -> setMode(CaptureMode.Photo)
            CaptureMode.Video -> {
                if (engine.recording.value) return
                val front = engine.lensFacing.value ==
                    androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                engine.setLens(
                    if (front) {
                        androidx.camera.core.CameraSelector.LENS_FACING_BACK
                    } else {
                        androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                    },
                    prefs.flash.value,
                )
            }
        }
    }

    /* ---------------- video ---------------- */

    /**
     * The shutter in video mode. Start, or stop — the same button, the way every camera does it.
     */
    fun toggleRecording() {
        if (engine.recording.value) {
            engine.stopRecording()
            return
        }
        val started = engine.startRecording(withAudio = audioGranted)
        if (!started) showNotice("Couldn't start recording")
    }

    /* ---------------- the shutter ---------------- */

    /**
     * Take a photograph.
     *
     * Ordered so that nothing that can be got wrong happens twice. The self timer runs
     * first; the capture is a single suspend call; processing and writing happen off the
     * main thread; and the roll decides at the end whether this frame is a photo yet.
     */
    fun shoot() {
        // In video mode the shutter is the record button. One control, two modes, which is what
        // every camera with a video mode has always done.
        if (videoMode()) {
            toggleRecording()
            return
        }
        if (_shooting.value) return
        val loadedRoll = roll.value
        if (loadedRoll != null && loadedRoll.finished) {
            showNotice("Roll finished — develop it")
            return
        }
        _shooting.value = true
        viewModelScope.launch {
            try {
                val timer = prefs.timer.value
                if (timer.seconds > 0 && captureRequestOutput == null) {
                    for (second in timer.seconds downTo 1) {
                        _countdown.value = second
                        delay(1_000)
                    }
                    _countdown.value = null
                }

                // Screen size never touches the shutter: the frame is already on the panel. This
                // is the whole of the fast path, and with a filter on it is the very frame you
                // were looking at rather than a second one processed to match.
                if (prefs.photoSize.value.isPreviewGrab) {
                    val grabbed = engine.previewFrame()
                    if (grabbed == null) {
                        showNotice("Nothing on the viewfinder yet")
                        return@launch
                    }
                    _shutterTick.tryEmit(Unit)
                    val activeFilter = filter.value
                    val seed = Random.nextFloat() * 1000f
                    val turn = engine.previewRotationDegrees()
                    val aspect = prefs.aspect.value
                    val stampAt = if (prefs.dateStamp.value) System.currentTimeMillis() else null
                    val processed = withContext(Dispatchers.Default) {
                        Frames.fromPreview(grabbed, turn, activeFilter, aspect, seed, stampAt)
                    }
                    finish(processed, activeFilter.id)
                    return@launch
                }

                val attempt = runCatching { engine.capture() }
                    .onFailure { Log.e(TAG, "capture failed", it) }
                val frame = attempt.getOrNull()
                if (frame == null) {
                    // Say *what* went wrong. "Shutter failed" cost a round trip to work out that
                    // zero-shutter-lag was accepting the configuration and then refusing every
                    // capture; the camera's own message would have named it.
                    val why = attempt.exceptionOrNull()?.message?.take(48)
                    showNotice(if (why.isNullOrBlank()) "Shutter failed" else "Shutter: $why")
                    return@launch
                }
                _shutterTick.tryEmit(Unit)

                val activeFilter = filter.value
                val aspect = prefs.aspect.value
                // A fresh seed per frame, so two shots of the same scene don't carry
                // identical grain — and so the grain in the file is not the grain that
                // happened to be on screen at the moment of the press.
                val seed = Random.nextFloat() * 1000f
                val stampAt = if (prefs.dateStamp.value) System.currentTimeMillis() else null
                val processed = withContext(Dispatchers.Default) {
                    Frames.process(frame, activeFilter, aspect, seed, stampAt)
                }

                finish(processed, activeFilter.id)
            } finally {
                _countdown.value = null
                _shooting.value = false
            }
        }
    }

    /**
     * Where a finished photograph goes, whichever way it was made.
     *
     * Shared by the capture path and the `Screen` grab so that the three destinations — another
     * app's `IMAGE_CAPTURE` request, a loaded roll, the gallery — are decided in exactly one place.
     * They were duplicated once and the roll branch was missing from the fast path.
     */
    private suspend fun finish(processed: Frames.Processed, filterId: String) {
        val output = captureRequestOutput
        if (output != null) {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .openOutputStream(output)?.use { it.write(processed.jpeg) }
                        ?: error("no stream")
                }.isSuccess
            }
            _captureRequestDone.tryEmit(ok)
            return
        }

        val takenAt = System.currentTimeMillis()
        val updated = filmRoll.expose(
            jpeg = processed.jpeg,
            takenAt = takenAt,
            filterId = filterId,
            width = processed.width,
            height = processed.height,
        )
        if (updated != null) {
            showNotice(
                if (updated.finished) "Roll finished" else "${updated.shot} of ${updated.length}",
            )
            return
        }

        val uri = repo.save(
            jpeg = processed.jpeg,
            takenAt = takenAt,
            width = processed.width,
            height = processed.height,
        )
        if (uri == null) showNotice("Couldn't save")
    }

    /* ---------------- the roll of film ---------------- */

    fun loadRoll() {
        filmRoll.load(prefs.rollLength.value)
        showNotice("Roll ${filmRoll.developedCount + 1} loaded")
    }

    fun developRoll() {
        val current = roll.value ?: return
        if (current.shot == 0) {
            filmRoll.discard()
            showNotice("Roll unloaded")
            return
        }
        viewModelScope.launch {
            val result = filmRoll.develop(repo)
            _developed.value = result
            refreshRoll()
            showNotice(
                when {
                    result.failed > 0 -> "${result.uris.size} developed, ${result.failed} stuck"
                    else -> "Roll ${result.number} developed"
                },
            )
        }
    }

    fun dismissDeveloped() {
        _developed.value = null
    }

    fun discardRoll() {
        filmRoll.discard()
        showNotice("Roll discarded")
    }

    /* ---------------- deleting ---------------- */

    fun trashRequest(photo: Photo) = repo.trashRequest(listOf(photo.uri))

    /* ---------------- notices ---------------- */

    private var noticeToken = 0

    fun showNotice(text: String) {
        val token = ++noticeToken
        _notice.value = text
        viewModelScope.launch {
            delay(NOTICE_MS)
            if (noticeToken == token) _notice.value = null
        }
    }

    override fun onCleared() {
        observer?.let { runCatching { it.close() } }
        observer = null
        engine.shutdown()
        beeps.release()
        thumbs.clear()
        super.onCleared()
    }

    private companion object {
        const val TAG = "CameraViewModel"
        const val NOTICE_MS = 1_400L
    }
}
