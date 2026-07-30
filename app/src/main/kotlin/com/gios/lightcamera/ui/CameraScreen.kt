package com.gios.lightcamera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gios.lightcamera.CaptureMode
import com.gios.lightcamera.Chrome
import com.gios.lightcamera.Colour
import com.gios.lightcamera.camera.AfMode
import com.gios.lightcamera.camera.AfState
import com.gios.lightcamera.camera.FaceMapper
import com.gios.lightcamera.camera.FlashMode
import com.gios.lightcamera.filter.ShaderRuntime
import com.gios.lightcamera.hw.CameraKeyAdvice
import com.gios.lightcamera.hw.WheelTurns
import com.gios.lightcamera.ui.theme.LightHaptics
import com.gios.lightcamera.ui.theme.LightIcons
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import com.gios.lightcamera.ui.theme.lightClickable
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/** How wide the strips of chrome down the left edge are. */
private val BAND = 54.dp

/**
 * The viewfinder, arranged the way LightOS's own camera is.
 *
 * The split that matters, and it took a screengrab of the real thing to see it: **the chrome is
 * written sideways and the picture is not**. In portrait the control band runs down the left
 * edge with `PHOTO ⌄` reading down it, while the image stays upright in the phone's own frame.
 * Turn the phone anticlockwise to shoot — the camera key comes round to the top edge, where a
 * shutter release belongs — and the band is along the bottom where a camera's controls are.
 *
 * So the band is wrapped in [HeldSideways] and the preview is left alone. An earlier version
 * rotated the whole app, which spun the image with it and turned the swipe down to the roll into
 * a sideways one.
 *
 * Nothing else is drawn over the picture: the band's strips take their own width out of the
 * left-hand side rather than floating, which is why there are no gradients anywhere here.
 */
@Composable
fun CameraScreen(
    vm: CameraViewModel,
    active: Boolean,
    onOpenRoll: () -> Unit,
    onOpenSettings: () -> Unit,
    rollSwipeEnabled: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colours = LightThemeTokens.colors
    val engine = vm.engine

    val filter by vm.filter.collectAsState()
    val mode by vm.prefs.mode.collectAsState()
    val chrome by vm.prefs.chrome.collectAsState()
    val flash by vm.prefs.flash.collectAsState()
    val timer by vm.prefs.timer.collectAsState()
    val afMode by vm.prefs.afMode.collectAsState()
    val facePriority by vm.prefs.facePriority.collectAsState()
    val wheelEnabled by vm.prefs.wheelEnabled.collectAsState()
    val colour by vm.prefs.colour.collectAsState()
    val roll by vm.roll.collectAsState()
    val faces by engine.faces.collectAsState()
    val afState by engine.afState.collectAsState()
    val focusPoint by engine.focusPoint.collectAsState()
    val zoom by engine.zoom.collectAsState()
    val ev by engine.ev.collectAsState()
    val evRange by engine.evRange.collectAsState()
    val torch by engine.torch.collectAsState()
    val notice by vm.notice.collectAsState()
    val countdown by vm.countdown.collectAsState()
    val recording by engine.recording.collectAsState()
    val recordSeconds by vm.recordSeconds.collectAsState()

    var frameWidth by remember { mutableStateOf(0) }
    var frameHeight by remember { mutableStateOf(0) }
    var gridOpen by remember { mutableStateOf(false) }
    var modeOpen by remember { mutableStateOf(false) }
    var evOpen by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE, and this is load-bearing rather than a compatibility hedge.
            // PERFORMANCE mode draws the camera into a SurfaceView, which the compositor hands
            // to the display on its own layer — a RenderEffect on that view filters nothing,
            // because the pixels never pass through the view hierarchy's draw. The TextureView
            // that COMPATIBLE uses is an ordinary hardware-layer view, so the shader applies.
            // Every filter in this app depends on it.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(previewView) { engine.bind(lifecycleOwner, previewView, flash) }

    // The microphone is asked for when you switch into video, never at the moment you press
    // record — a dialog in front of the thing you were filming is worse than silent footage.
    val askAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.audioGranted = granted }
    LaunchedEffect(mode) {
        if (mode != CaptureMode.Video) return@LaunchedEffect
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        vm.audioGranted = has
        if (!has) askAudio.launch(Manifest.permission.RECORD_AUDIO)
    }

    /* ---- colour, and saying so when it can't ---- */

    val wantsColour = active && colour != Colour.Off
    ColourEffect(enabled = wantsColour)
    LaunchedEffect(wantsColour) {
        if (!wantsColour) return@LaunchedEffect
        if (ColorMode.granted(context) || ColorMode.phoneIsColour(context)) return@LaunchedEffect
        // Nothing this app can do about it from in here, so say what will: the panel is a
        // colour panel and one adb line unlocks it.
        vm.showNotice("Colour needs an adb grant — see settings")
    }

    /* ---- grain that moves ---- */

    var seed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(filter, active) {
        if (!filter.animated || !active) return@LaunchedEffect
        while (true) {
            seed = Random.nextFloat() * 1000f
            delay(100)
        }
    }

    // The live filter, attached in exactly one place. In video it is forced off: a RenderEffect
    // is a property of the *view*, so it never reaches the recorded stream — a filtered preview
    // would be promising something the file wouldn't deliver.
    val liveFilter = if (mode == CaptureMode.Video) com.gios.lightcamera.filter.Filters.none else filter
    LaunchedEffect(liveFilter, seed, frameWidth, frameHeight) {
        previewView.setRenderEffect(
            ShaderRuntime.effectFor(liveFilter, frameWidth, frameHeight, seed),
        )
    }
    DisposableEffect(Unit) { onDispose { previewView.setRenderEffect(null) } }

    /* ---- the wheel ---- */

    // **A bare turn walks the filters**, grid open or not — that is what the wheel is for on this
    // camera. The phone has no optical zoom and the stock app offers none, so a dial spent on
    // digital crop was a dial spent on nothing; a dial that changes what the photograph looks
    // like earns every notch. Unarmed, because each notch has to count, and None is three notches
    // wide on the track so a stray one lands somewhere harmless.
    WheelTurns(active = active && wheelEnabled && !evOpen, armed = false) { notches ->
        vm.stepFilter(if (notches > 0) 1 else -1)
    }
    // Exposure keeps both of its routes: the strip while it is open, and hold-and-turn always.
    WheelTurns(active = active && wheelEnabled && evOpen, armed = true) { notches ->
        engine.stepEv(notches)
    }
    WheelTurns(active = active && wheelEnabled, armed = true, pressed = true) { notches ->
        engine.stepEv(notches)
        vm.showNotice("EV ${engine.evLabel()}")
    }

    /* ---- the shutter blink ---- */

    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        vm.shutterTick.collect {
            blink = 1f
            repeat(4) {
                delay(16)
                blink -= 0.25f
            }
            blink = 0f
        }
    }

    val tilt by rememberTilt(active = active)
    val levelVisible = rememberLevelVisible(tilt, enabled = active)
    val priority = remember(faces, frameWidth, frameHeight, facePriority) {
        if (facePriority) FaceMapper.priority(faces, frameWidth, frameHeight) else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(Modifier.fillMaxSize()) {
            /* ---------------- the band, written sideways ---------------- */
            Box(
                Modifier
                    .width(BAND)
                    .fillMaxHeight(),
            ) {
                HeldSideways {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChromeIcon(
                            icon = LightIcons.Album,
                            onClick = onOpenRoll,
                            lighten = !rollSwipeEnabled,
                        )
                        Spacer(Modifier.weight(1f))
                        // The stock camera's "PHOTO ⌄": what the camera is set to, and a
                        // chevron that opens the picker.
                        Row(
                            modifier = Modifier
                                .lightClickable { modeOpen = !modeOpen }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LightText(
                                text = mode.bandLabel,
                                variant = LightTextVariant.Button,
                                align = TextAlign.Center,
                            )
                            Spacer(Modifier.width(7.dp))
                            Chevron(pointingUp = modeOpen)
                        }
                        Spacer(Modifier.weight(1f))
                        ChromeIcon(
                            icon = when (flash) {
                                FlashMode.Off -> LightIcons.FlashOff
                                FlashMode.On -> LightIcons.FlashOn
                                FlashMode.Auto -> LightIcons.FlashAuto
                            },
                            lighten = flash == FlashMode.Off,
                            onClick = {
                                vm.prefs.setFlash(
                                    when (flash) {
                                        FlashMode.Off -> FlashMode.Auto
                                        FlashMode.Auto -> FlashMode.On
                                        FlashMode.On -> FlashMode.Off
                                    },
                                )
                            },
                        )
                        ChromeIcon(
                            icon = LightIcons.Exposure,
                            lighten = !evOpen && ev == 0,
                            onClick = {
                                if (evRange.first == evRange.last) {
                                    vm.showNotice("No exposure control")
                                } else {
                                    evOpen = !evOpen
                                }
                            },
                        )
                    }
                }
            }

            /* ---------------- the picture, upright ---------------- */
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            frameWidth = it.width
                            frameHeight = it.height
                            vm.onViewSized(it.width, it.height)
                        }
                        .viewfinderGestures(
                            enabled = active,
                            onTapFocus = { x, y ->
                                // A buzz for the *ask*. The buzz and beep for the lens landing
                                // come off the camera's own AF result, in the view model.
                                LightHaptics.advance(context)
                                engine.focusAt(x, y, lock = false)
                            },
                            onDoubleTap = { vm.flipLens() },
                            onFilterStep = { vm.stepFilter(it) },
                        ),
                )

                FrameOverlay(
                    chrome = chrome,
                    faces = faces,
                    priority = priority,
                    afState = afState,
                    focusPoint = focusPoint,
                    tilt = tilt,
                    levelVisible = levelVisible,
                    modifier = Modifier.fillMaxSize(),
                )

                if (blink > 0f) {
                    Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = blink)) }
                }

                if (countdown != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LightText("$countdown", LightTextVariant.Title)
                    }
                }

                // The two things allowed on the image, both in the corner: how autofocus is
                // set, and what is happening right now.
                Row(
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (recording) {
                        RecordDot()
                        LightText(
                            " ${"%d:%02d".format(recordSeconds / 60, recordSeconds % 60)}",
                            LightTextVariant.Micro,
                        )
                    } else {
                        AfBadge(mode = afMode, state = afState)
                    }
                    if (torch) {
                        LightText(
                            " TORCH",
                            LightTextVariant.Micro,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (zoom > 1.02f) {
                        LightText(
                            " ${engine.zoomLabel()}",
                            LightTextVariant.Micro,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (ev != 0) {
                        LightText(
                            " EV ${engine.evLabel()}",
                            LightTextVariant.Micro,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (timer.seconds > 0 && mode != CaptureMode.Video) {
                        LightText(
                            " ${timer.label}",
                            LightTextVariant.Micro,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (filter.agsl != null && mode != CaptureMode.Video) {
                        LightText(
                            " ${filter.label.uppercase()}",
                            LightTextVariant.Micro,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                Notice(
                    text = notice,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                )

                // No progress bar while the shutter works. There was a hairline across the
                // bottom of the frame here, and it was the wrong answer twice over: it drew on
                // the picture, which nothing else in this app is allowed to do, and it was
                // apologising for a wait that v1.8 mostly removed. The blink is the feedback.
            }
        }

        // The strips that open out of the band, drawn over the picture's left edge rather than
        // taking width from it: resizing the preview would rebind the shader and reflow the
        // frame, and a menu should never cost you your framing.
        if (modeOpen) {
            ModeStrip(
                mode = mode,
                onPick = {
                    vm.setMode(it)
                    modeOpen = false
                },
                onFilters = {
                    modeOpen = false
                    gridOpen = true
                },
                onSettings = {
                    modeOpen = false
                    onOpenSettings()
                },
                modifier = Modifier.padding(start = BAND),
            )
        }
        if (evOpen) {
            ExposureStrip(
                index = ev,
                range = evRange,
                label = engine.evLabel(),
                onStep = { engine.stepEv(it) },
                onReset = { engine.resetEv() },
                modifier = Modifier.padding(start = BAND),
            )
        }
        if (roll != null && mode != CaptureMode.Video) {
            // The film counter down the far edge, opposite the band: it belongs to the
            // photograph rather than to the controls.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(BAND)
                    .fillMaxHeight(),
            ) {
                HeldSideways {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SprocketStrip(offsetFrames = roll?.shot ?: 0)
                        RollCounter(
                            roll = roll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .lightClickable { onOpenSettings() },
                        )
                    }
                }
            }
        }

        if (gridOpen) {
            FilterGrid(
                vm = vm,
                previewView = previewView,
                onPick = { id ->
                    vm.setFilter(id)
                    gridOpen = false
                },
                onOpenSettings = {
                    gridOpen = false
                    onOpenSettings()
                },
                onClose = { gridOpen = false },
            )
        }
    }

    LaunchedEffect(Unit) {
        if (CameraKeyAdvice.problem(context) != null) {
            vm.showNotice("Camera key held — see settings")
        }
    }
}

/**
 * Camera, Video, Selfie — the stock camera's three, out of the same slot and in the same order.
 *
 * A strip beside the band rather than a sheet over the picture, so it reads as the band opening
 * out. Filters and settings are on the end of it because the viewfinder has no room for them and
 * this is the one menu in the app.
 */
@Composable
private fun ModeStrip(
    mode: CaptureMode,
    onPick: (CaptureMode) -> Unit,
    onFilters: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = LightThemeTokens.colors
    Box(
        modifier = modifier
            .width(BAND + 34.dp)
            .fillMaxHeight()
            .background(colours.background),
    ) {
        HeldSideways {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CaptureMode.entries.forEach { candidate ->
                    val here = candidate == mode
                    Box(
                        modifier = Modifier
                            .lightClickable { onPick(candidate) }
                            .background(if (here) colours.content else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        LightText(
                            text = candidate.label.uppercase(),
                            variant = LightTextVariant.Superfine,
                            color = if (here) colours.background else colours.content,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                ChromeLabel(text = "Filters", onClick = onFilters, lighten = true)
                ChromeIcon(icon = LightIcons.Settings, lighten = true, onClick = onSettings)
            }
        }
    }
}

/**
 * Exposure compensation, as a row of stops.
 *
 * Opened from the brightness icon, and while it is open the bare wheel drives it — which is the
 * whole reason it is a mode rather than a slider. The wheel is a better exposure dial than a
 * thumb on a 3.92" screen will ever be.
 */
@Composable
private fun ExposureStrip(
    index: Int,
    range: IntRange,
    label: String,
    onStep: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = LightThemeTokens.colors
    Box(
        modifier = modifier
            .width(BAND)
            .fillMaxHeight()
            .background(colours.background),
    ) {
        HeldSideways {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightText(
                    "−",
                    LightTextVariant.Copy,
                    modifier = Modifier
                        .lightClickable { onStep(-1) }
                        .padding(horizontal = 8.dp),
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .lightClickable { onReset() },
                ) {
                    val span = (range.last - range.first).coerceAtLeast(1)
                    val pitch = size.width / span
                    for (i in 0..span) {
                        val x = i * pitch
                        // Whole stops taller than the thirds between them, so the scale can be
                        // read without labels.
                        val whole = (range.first + i) % 3 == 0
                        val h = if (whole) size.height else size.height * 0.45f
                        drawLine(
                            color = colours.contentSecondary.copy(alpha = 0.55f),
                            start = Offset(x, size.height - h),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    val here = (index - range.first) * pitch
                    drawLine(
                        color = colours.content,
                        start = Offset(here, 0f),
                        end = Offset(here, size.height),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Square,
                    )
                }
                LightText(
                    "+",
                    LightTextVariant.Copy,
                    modifier = Modifier
                        .lightClickable { onStep(1) }
                        .padding(horizontal = 8.dp),
                )
                LightText(
                    text = label,
                    variant = LightTextVariant.Micro,
                    modifier = Modifier.width(32.dp),
                    align = TextAlign.End,
                )
            }
        }
    }
}

/**
 * The little chevron next to the mode. Drawn rather than an icon: every arrow glyph in the SDK
 * is bigger and heavier than the one beside "PHOTO" on the stock camera.
 */
@Composable
private fun Chevron(pointingUp: Boolean) {
    val colours = LightThemeTokens.colors
    Canvas(Modifier.width(9.dp).height(6.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.4.dp.toPx()
        val tipY = if (pointingUp) 0f else h
        val baseY = if (pointingUp) h else 0f
        drawLine(colours.content, Offset(0f, baseY), Offset(w / 2f, tipY), stroke, StrokeCap.Round)
        drawLine(colours.content, Offset(w, baseY), Offset(w / 2f, tipY), stroke, StrokeCap.Round)
    }
}

/** Recording. A filled disc, because that is what a record light is. */
@Composable
private fun RecordDot() {
    val colours = LightThemeTokens.colors
    Canvas(Modifier.size(9.dp)) {
        drawCircle(color = colours.content, radius = size.minDimension / 2f)
    }
}

/**
 * The autofocus badge. `AF-S` or `AF-C`, inverted while the lens is locked.
 *
 * Inversion rather than a colour, because the panel has none to spare and LightOS carries state
 * by swapping foreground and background everywhere else too. It is the one permanent mark on the
 * image and it earns the room: autofocus that gives no sign of being on is autofocus you press
 * twice.
 */
@Composable
private fun AfBadge(mode: AfMode, state: AfState, modifier: Modifier = Modifier) {
    val colours = LightThemeTokens.colors
    val locked = state == AfState.Locked
    Box(
        modifier = modifier
            .background(if (locked) colours.content else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        LightText(
            text = if (mode == AfMode.Single) "AF-S" else "AF-C",
            variant = LightTextVariant.Micro,
            color = when {
                locked -> colours.background
                state == AfState.Scanning -> colours.content
                else -> colours.contentSecondary
            },
        )
    }
}

/**
 * Tap to focus, double tap to switch lens, swipe sideways for the next filter.
 *
 * Written against [PointerEventPass.Initial] and arbitrating by hand, because the viewfinder
 * sits inside a vertical pager: on the main pass the pager has already claimed the gesture. The
 * axis is decided once, past the slop, and only a horizontal decision is consumed — the vertical
 * one is left entirely alone, which is what keeps the swipe down to the roll working.
 */
private fun Modifier.viewfinderGestures(
    enabled: Boolean,
    onTapFocus: (Float, Float) -> Unit,
    onDoubleTap: () -> Unit,
    onFilterStep: (Int) -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val slopPx = 14.dp.toPx()
        val swipePx = 52.dp.toPx()
        // Kept across gestures, which is the only way to see a double tap: two taps are two
        // complete gestures, and the second only means anything in the light of the first.
        var lastTapAt = 0L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            var horizontal = false
            var decided = false
            var fired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (!decided && (abs(dx) > slopPx || abs(dy) > slopPx)) {
                    decided = true
                    horizontal = abs(dx) > abs(dy) * 1.3f
                }
                if (horizontal) {
                    event.changes.forEach { it.consume() }
                    if (!fired && abs(dx) > swipePx) {
                        fired = true
                        onFilterStep(if (dx < 0) 1 else -1)
                    }
                }
            }
            if (!decided && abs(dx) < slopPx && abs(dy) < slopPx) {
                val now = System.currentTimeMillis()
                if (now - lastTapAt < DOUBLE_TAP_MS) {
                    lastTapAt = 0L
                    onDoubleTap()
                } else {
                    lastTapAt = now
                    // The first tap focuses regardless. Waiting to find out whether a second is
                    // coming would put a third of a second of lag on every tap to focus, to save
                    // one pointless autofocus on the rare double.
                    onTapFocus(down.position.x, down.position.y)
                }
            }
        }
    },
)

/** Long enough to be deliberate, short enough that two taps to focus aren't one gesture. */
private const val DOUBLE_TAP_MS = 320L
