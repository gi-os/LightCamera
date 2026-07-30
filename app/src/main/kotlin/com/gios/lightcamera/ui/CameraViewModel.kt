package com.gios.lightcamera.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lightcamera.Prefs
import com.gios.lightcamera.camera.CameraEngine
import com.gios.lightcamera.camera.FaceBox
import com.gios.lightcamera.camera.Frames
import com.gios.lightcamera.filter.Filters
import com.gios.lightcamera.media.MediaStoreRepo
import com.gios.lightcamera.media.Photo
import com.gios.lightcamera.media.RollScope
import com.gios.lightcamera.media.Thumbs
import com.gios.lightcamera.roll.FilmRoll
import com.gios.lightcamera.roll.Roll
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

    private var observer: AutoCloseable? = null
    private var lastPriorityFace: FaceBox? = null

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
    }

    @Volatile private var viewWidth = 0

    @Volatile private var viewHeight = 0

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

    fun stepFilter(by: Int) {
        val next = Filters.step(filter.value, by)
        prefs.setFilter(next.id)
        showNotice(next.label)
    }

    fun setFilter(id: String) {
        prefs.setFilter(id)
        showNotice(Filters.byId(id).label)
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

                val frame = runCatching { engine.capture() }
                    .onFailure { Log.e(TAG, "capture failed", it) }
                    .getOrNull()
                if (frame == null) {
                    showNotice("Shutter failed")
                    return@launch
                }
                _shutterTick.tryEmit(Unit)

                val activeFilter = filter.value
                val aspect = prefs.aspect.value
                // A fresh seed per frame, so two shots of the same scene don't carry
                // identical grain — and so the grain in the file is not the grain that
                // happened to be on screen at the moment of the press.
                val seed = Random.nextFloat() * 1000f
                val processed = withContext(Dispatchers.Default) {
                    Frames.process(frame, activeFilter, aspect, seed)
                }

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
                    return@launch
                }

                val takenAt = System.currentTimeMillis()
                val updated = filmRoll.expose(
                    jpeg = processed.jpeg,
                    takenAt = takenAt,
                    filterId = activeFilter.id,
                    width = processed.width,
                    height = processed.height,
                )
                if (updated != null) {
                    showNotice(
                        if (updated.finished) {
                            "Roll finished"
                        } else {
                            "${updated.shot} of ${updated.length}"
                        },
                    )
                    return@launch
                }

                val uri = repo.save(
                    jpeg = processed.jpeg,
                    takenAt = takenAt,
                    width = processed.width,
                    height = processed.height,
                )
                if (uri == null) showNotice("Couldn't save")
            } finally {
                _countdown.value = null
                _shooting.value = false
            }
        }
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
        thumbs.clear()
        super.onCleared()
    }

    private companion object {
        const val TAG = "CameraViewModel"
        const val NOTICE_MS = 1_400L
    }
}
