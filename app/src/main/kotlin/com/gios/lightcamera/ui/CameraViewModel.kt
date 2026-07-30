package com.gios.lightcamera.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lightcamera.CaptureMode
import com.gios.lightcamera.Prefs
import com.gios.lightcamera.camera.CameraEngine
import com.gios.lightcamera.camera.DateStamp
import com.gios.lightcamera.camera.FaceBox
import com.gios.lightcamera.PhotoSize
import com.gios.lightcamera.camera.FrameAspect
import com.gios.lightcamera.camera.Frames
import com.gios.lightcamera.camera.PuriArt
import com.gios.lightcamera.camera.PuriStrip
import com.gios.lightcamera.filter.FaceQuads
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    /**
     * The roll, narrowed to the starred ones when that is the scope.
     *
     * Derived rather than re-queried: starring a photograph should update the grid immediately, and a
     * round trip to MediaStore for a filter this app already has in memory would be slower and would
     * flicker. Declared here, above `init`, for the reason at the top of that block.
     */
    val photos: StateFlow<List<Photo>> = combine(
        _photos,
        prefs.favourites,
        prefs.scope,
    ) { list, starred, scope ->
        if (scope == RollScope.Favourites) list.filter { it.name in starred } else list
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /**
     * The seed everything random about a Purikura comes from — which stickers, where, which date.
     *
     * **Held still between shots, and that is the whole point of it existing.** The shader's own
     * `seed` moves ten times a second so the glitter twinkles; if the stickers came off that they
     * would rearrange themselves while you were composing, and the viewfinder would be showing you
     * something other than what you were about to get. This one changes when you take a photograph.
     */
    private val _puriSeed = MutableStateFlow(Random.nextLong())
    val puriSeed: StateFlow<Long> = _puriSeed.asStateFlow()

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

        // **Simple sits one notch before None on the same track.** The wheel is the one control this phone
        // has that a camera doesn't, and taking it away in the mode you spend most of your time in would
        // waste it — so a turn out of Simple lands on Pro with no filter, and carries on into the filters
        // from there. A turn back at None returns to Simple. One dial, one line: Simple, None, Film, Mono,
        // and so on.
        if (prefs.mode.value.isSimple) {
            if (by <= 0) return
            setMode(CaptureMode.Photo)
            prefs.setFilter(Filters.none.id)
            dialHeldUntil = now + Filters.NONE_DWELL_MS
            return
        }
        if (by < 0 && filter.value.id == Filters.none.id) {
            setMode(CaptureMode.Simple)
            dialHeldUntil = now + Filters.NONE_DWELL_MS
            return
        }

        val next = Filters.step(filter.value, by)
        prefs.setFilter(next.id)
        dialHeldUntil = now + Filters.dwellMs(next)
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
        // **Simple drops Auto flash.** Auto is not free even when it decides not to fire: the HAL runs a
        // precapture metering sequence — often a preflash — before it will start the frame you asked for,
        // which is most of a second that a mode whose whole argument is speed should not be spending. Off
        // by default there; explicitly turning it on in Simple still works.
        if (next.isSimple && prefs.flash.value == FlashMode.Auto) prefs.setFlash(FlashMode.Off)
        prefs.setMode(next)
        engine.setMode(next, prefs.flash.value)
        showNotice(next.bandLabel)
    }

    /** The lens switch, which in Photo and Selfie is the same thing as switching mode. */
    fun flipLens() {
        when (prefs.mode.value) {
            CaptureMode.Simple, CaptureMode.Photo -> setMode(CaptureMode.Selfie)
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
     * A new arrangement of stickers. Called after each Purikura, and when the frame changes so that
     * flicking through the borders also reshuffles what is on them.
     */
    fun reshufflePuri() {
        _puriSeed.value = Random.nextLong()
    }

    /**
     * The frame this photograph will have, Random resolved from the seed.
     *
     * The seed is the one held still between shots, so the answer is stable while you compose and
     * changes when you shoot — which is what makes Random honest rather than a surprise.
     */
    fun puriFrame(): PuriArt.Frame = PuriArt.resolveFrame(prefs.puriFrame.value, _puriSeed.value)

    /** The strip this press will take, Random resolved the same way. */
    fun puriStripLayout(): PuriStrip.Layout =
        PuriStrip.resolveLayout(prefs.puriStrip.value, _puriSeed.value)

    /** The four frames behind a strip, for the viewer's button. Empty for an ordinary photograph. */
    suspend fun framesBehind(photo: Photo): List<Photo> =
        if (photo.name.contains("_strip")) repo.framesOf(photo.name) else emptyList()

    /**
     * What to draw on top of a Purikura, or null if this is not one.
     *
     * Built here rather than in the shutter so the viewfinder can call the same function with the
     * same seed and show the truth. [faces] arrive after the turn and the crop, which is why this is
     * a lambda taking them rather than a plan made in advance.
     */
    fun puriOverlay(
        filter: Filters.Filter,
        withDate: Boolean,
        millis: Long,
    ): ((android.graphics.Canvas, Int, Int, List<com.gios.lightcamera.filter.FaceQuad>) -> Unit)? {
        if (!filter.facesAware) return null
        val frame = puriFrame()
        val faceStickers = prefs.puriFaceStickers.value
        val marginStickers = prefs.puriMarginStickers.value
        val dateId = if (withDate) prefs.puriDate.value else PuriArt.OFF
        val seed = _puriSeed.value
        return { canvas, w, h, faces ->
            PuriArt.draw(
                canvas = canvas,
                w = w,
                h = h,
                frame = frame,
                plan = PuriArt.plan(seed, faces, faceStickers, marginStickers, dateId),
                millis = millis,
            )
        }
    }

    /**
     * When to write a date on this frame, or null for no stamp.
     *
     * Three separate settings rather than one, because the stamp belongs on a plain photograph and
     * fights with a coarse filter — a full-precision date over a 160-cell dither reads as a caption
     * stuck on top rather than something the camera did.
     */
    private fun stampTime(filter: Filters.Filter): Long? {
        val wanted = when {
            filter.agsl == null -> prefs.stampPlain.value
            filter.lowRes -> prefs.stampCoarse.value
            else -> prefs.stampFiltered.value
        }
        return if (wanted) System.currentTimeMillis() else null
    }

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
        // **Simple: the shortest route from a press to a file.** No filter, no crop, no stamp, no timer
        // and no roll, so `Frames.process` recognises that there is nothing to do and writes the sensor's
        // own JPEG straight out — no decode of a huge bitmap, no re-encode, EXIF intact. Everything below
        // this branch exists to serve the options Simple does not have.
        if (prefs.mode.value.isSimple) {
            shootSimple()
            return
        }
        // Four shots and a strip, if that is what the menu says. Its own routine, because a booth
        // sequence is not a photograph taken four times: it counts you in, it cannot be stopped
        // halfway, and what it produces is one print.
        if (PuriStrip.enabled(prefs.puriStrip.value) && filter.value.facesAware && !videoMode()) {
            shootStrip(puriStripLayout())
            return
        }
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
                //
                // **The coarse filters always come this way too, whatever the size is set to.** A
                // Game Boy frame is 160 cells wide by definition; capturing 12MP to throw all of it
                // into those cells costs a second and a half and changes nothing in the file. So the
                // size setting governs the photographs where resolution is a real quantity, and the
                // ones where it isn't just take the panel.
                if (prefs.photoSize.value.isPreviewGrab ||
                    filter.value.lowRes ||
                    filter.value.facesAware
                ) {
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
                    val stampAt = stampTime(activeFilter)
                    // The faces as the preview found them, in the preview's own pixels. `fromPreview`
                    // carries them through the turn and the crop, so the warp stays on the face.
                    val faces = if (activeFilter.facesAware) {
                        FaceQuads.of(engine.faces.value, grabbed.width, grabbed.height)
                    } else {
                        emptyList()
                    }
                    // A Purikura brings its own date — a bubble capsule, a ticket stub, one of
                    // eight — so the ordinary date back stands down rather than both of them
                    // printing into the same corner.
                    // A Purikura's date is its own switch in its own menu, not the date back's:
                    // they are different objects that happen to both be dates, and one of them is
                    // random by design.
                    val puri = puriOverlay(
                        filter = activeFilter,
                        withDate = prefs.puriDate.value != PuriArt.OFF,
                        millis = System.currentTimeMillis(),
                    )
                    val processed = withContext(Dispatchers.Default) {
                        Frames.fromPreview(
                            preview = grabbed,
                            rotationDegrees = turn,
                            filter = activeFilter,
                            aspect = aspect,
                            seed = seed,
                            stampAt = if (puri != null) null else stampAt,
                            stampStyle = prefs.stampStyle.value,
                            faces = faces,
                            overlay = puri,
                            tune = prefs.puriTune(),
                        )
                    }
                    finish(processed, activeFilter.id)
                    // A fresh arrangement for the next one, so two shots in a row are not the same
                    // print with a different face in it.
                    if (puri != null) reshufflePuri()
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
                val stampAt = stampTime(activeFilter)
                val processed = withContext(Dispatchers.Default) {
                    Frames.process(frame, activeFilter, aspect, seed, stampAt, prefs.stampStyle.value)
                }

                finish(processed, activeFilter.id)
            } finally {
                _countdown.value = null
                _shooting.value = false
            }
        }
    }

    /**
     * A photograph, and nothing else.
     *
     * The three things that make this quick, in order of how much they matter:
     *
     *  1. **Nothing to process.** With no filter, no crop and no date the JPEG the ISP produced is the
     *     file — `Frames.process` returns it whole. A filtered 12MP shot has to be decoded to a 48MB
     *     bitmap, run through a shader and re-encoded; skipping that is most of a second.
     *  2. **Twelve megapixels, not fifty.** Reading out and encoding the full sensor is most of the ISP's
     *     second on its own, and each step down is roughly a halving. 12MP is four times the largest
     *     print anyone makes from a phone.
     *  3. **No waiting for focus.** Continuous AF is already running and already converged on whatever
     *     you are pointing at; a press means take it now, not focus and then take it. The two-stage
     *     shutter is a Pro feature.
     *
     * The size is set for the duration and put back afterwards, so a trip through Simple does not quietly
     * rewrite a Pro setting.
     */
    private fun shootSimple() {
        _shooting.value = true
        viewModelScope.launch {
            val wanted = prefs.photoSize.value
            try {
                if (wanted != PhotoSize.Large) prefs.setPhotoSize(PhotoSize.Large)
                val attempt = runCatching { engine.capture() }
                    .onFailure { Log.e(TAG, "simple capture failed", it) }
                val frame = attempt.getOrNull()
                if (frame == null) {
                    val why = attempt.exceptionOrNull()?.message?.take(48)
                    showNotice(if (why.isNullOrBlank()) "Shutter failed" else "Shutter: $why")
                    return@launch
                }
                _shutterTick.tryEmit(Unit)

                // Written whole, exactly as the ISP made it. No decode, no re-encode, EXIF intact.
                val takenAt = System.currentTimeMillis()
                val size = Frames.sizeOf(frame.jpeg)
                val uri = repo.save(
                    jpeg = frame.jpeg,
                    takenAt = takenAt,
                    width = size.first,
                    height = size.second,
                )
                if (uri == null) {
                    showNotice("Couldn't save")
                    return@launch
                }

                // **The date goes on afterwards.** Printing it means decoding a 12MP JPEG, drawing, and
                // encoding again — a second of work that has no business being between your finger and the
                // photograph. So the shutter is already free by the time this starts, and the file gains
                // its date a moment later while you are framing the next one. The photograph is safe on
                // disk either way: if this fails, or the app dies first, what is left is an undated
                // photograph rather than none.
                if (prefs.stampPlain.value) {
                    launch {
                        val stamped = withContext(Dispatchers.Default) {
                            DateStamp.applyTo(frame.jpeg, takenAt, prefs.stampStyle.value)
                        }
                        if (stamped != null) repo.rewrite(uri, stamped)
                        refreshRoll()
                    }
                }
            } finally {
                if (prefs.photoSize.value != wanted) prefs.setPhotoSize(wanted)
                _shooting.value = false
            }
        }
    }

    /**
     * Four photographs, three seconds apart, and then the strip.
     *
     * Each frame goes through exactly the same path a single Purikura does — same shader, same frame,
     * same stickers, same date — so a frame off a strip is indistinguishable from one taken on its
     * own. What differs is where they are saved: the four go into a folder the roll does not show, and
     * the strip goes into the camera roll as the one photograph you took.
     *
     * The stickers are **reshuffled between frames**, which is deliberate. A booth's four panels are
     * four different decorations of the same three seconds, and a strip with identical cat ears in
     * every panel looks like a mistake rather than a set.
     */
    private fun shootStrip(layout: PuriStrip.Layout) {
        _shooting.value = true
        viewModelScope.launch {
            val bitmaps = ArrayList<Bitmap>(PuriStrip.SHOTS)
            val takenAt = System.currentTimeMillis()
            try {
                for (shot in 1..PuriStrip.SHOTS) {
                    // Count in before every frame, including the first: a booth gives you a moment
                    // to arrange your face, and the first one is the one you are least ready for.
                    for (second in STRIP_GAP_SECONDS downTo 1) {
                        _countdown.value = second
                        delay(1_000)
                    }
                    _countdown.value = null
                    showNotice("$shot of ${PuriStrip.SHOTS}")

                    val grabbed = engine.previewFrame()
                    if (grabbed == null) {
                        showNotice("Nothing on the viewfinder yet")
                        return@launch
                    }
                    _shutterTick.tryEmit(Unit)
                    val activeFilter = filter.value
                    val faces = FaceQuads.of(engine.faces.value, grabbed.width, grabbed.height)
                    // **No date on the panels.** A booth prints it once, in the margin of the strip,
                    // because the four photographs are one object — four copies of the same date down a
                    // strip is a bug that looks like a feature. It goes on the sheet below.
                    val puri = puriOverlay(
                        filter = activeFilter,
                        withDate = false,
                        millis = takenAt,
                    )
                    val processed = withContext(Dispatchers.Default) {
                        Frames.fromPreview(
                            preview = grabbed,
                            rotationDegrees = engine.previewRotationDegrees(),
                            filter = activeFilter,
                            aspect = prefs.aspect.value,
                            seed = Random.nextFloat() * 1000f,
                            faces = faces,
                            overlay = puri,
                            tune = prefs.puriTune(),
                        )
                    }
                    repo.save(
                        jpeg = processed.jpeg,
                        takenAt = takenAt,
                        width = processed.width,
                        height = processed.height,
                        suffix = shot.toString(),
                        hidden = true,
                    )
                    withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(processed.jpeg, 0, processed.jpeg.size)
                    }?.let { bitmaps += it }
                    reshufflePuri()
                }

                // No date on a strip, in the panels or on the print. The layouts that want one have a
                // footer of their own, which the composer fills in.
                val sheet = withContext(Dispatchers.Default) {
                    PuriStrip.compose(bitmaps, layout, puriFrame(), takenAt)
                }
                if (sheet == null) {
                    showNotice("Couldn't build the strip")
                    return@launch
                }
                val jpeg = withContext(Dispatchers.Default) {
                    java.io.ByteArrayOutputStream(sheet.width * sheet.height / 6).also {
                        sheet.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    }.toByteArray()
                }
                val uri = repo.save(
                    jpeg = jpeg,
                    takenAt = takenAt,
                    width = sheet.width,
                    height = sheet.height,
                    suffix = "strip",
                )
                if (uri == null) showNotice("Couldn't save the strip") else showNotice("Strip saved")
                sheet.recycle()
            } finally {
                bitmaps.forEach { it.recycle() }
                _countdown.value = null
                _shooting.value = false
                refreshRoll()
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

        /** The count-in before each frame of a strip. Long enough to change your face, not your mind. */
        const val STRIP_GAP_SECONDS = 3
        const val NOTICE_MS = 1_400L
    }
}
