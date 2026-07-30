package com.gios.lightcamera.ui

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gios.lightcamera.Chrome
import com.gios.lightcamera.camera.AfMode
import com.gios.lightcamera.camera.AfState
import com.gios.lightcamera.camera.FaceMapper
import com.gios.lightcamera.camera.FlashMode
import com.gios.lightcamera.filter.ShaderRuntime
import com.gios.lightcamera.hw.WheelTurns
import com.gios.lightcamera.ui.theme.LightHaptics
import com.gios.lightcamera.ui.theme.LightIcon
import com.gios.lightcamera.ui.theme.LightIcons
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import com.gios.lightcamera.ui.theme.lightClickable
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/**
 * The viewfinder, built to look like LightOS's own camera.
 *
 * The stock app is one picture with a single row of controls in a black band beneath it, and
 * copying that arrangement settles a lot of arguments at once:
 *
 *  - **Nothing is drawn over the image.** No floating chrome, no gradients, no readouts on
 *    top of the frame. The band below is where the controls live, so the picture is only ever
 *    the picture.
 *  - **There is no shutter button.** The phone has a two-stage shutter release on its side;
 *    a circle on the glass duplicating it would only take room from the image and teach the
 *    wrong gesture. Tapping the frame focuses instead, which is the thing a touchscreen is
 *    actually better at. (Should the camera key be dead, LightControl is swallowing it — see
 *    the README.)
 *  - **The band reads left to right as album, lens, mode, flash, brightness** — the stock
 *    order, with the mode slot doing the job "PHOTO ⌄" does there: it names what the camera
 *    is set to and opens the picker. Here that is the filter.
 *
 * The system bars are hidden, so the image starts at the very top edge of the panel exactly
 * as it does in the stock app.
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
    val chrome by vm.prefs.chrome.collectAsState()
    val flash by vm.prefs.flash.collectAsState()
    val timer by vm.prefs.timer.collectAsState()
    val afMode by vm.prefs.afMode.collectAsState()
    val facePriority by vm.prefs.facePriority.collectAsState()
    val wheelEnabled by vm.prefs.wheelEnabled.collectAsState()
    val roll by vm.roll.collectAsState()
    val faces by engine.faces.collectAsState()
    val afState by engine.afState.collectAsState()
    val focusPoint by engine.focusPoint.collectAsState()
    val lensFacing by engine.lensFacing.collectAsState()
    val zoom by engine.zoom.collectAsState()
    val ev by engine.ev.collectAsState()
    val evRange by engine.evRange.collectAsState()
    val torch by engine.torch.collectAsState()
    val notice by vm.notice.collectAsState()
    val countdown by vm.countdown.collectAsState()
    val shooting by vm.shooting.collectAsState()

    var frameWidth by remember { mutableStateOf(0) }
    var frameHeight by remember { mutableStateOf(0) }
    var gridOpen by remember { mutableStateOf(false) }
    var evOpen by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE, and this is load-bearing rather than a compatibility hedge.
            // PERFORMANCE mode draws the camera into a SurfaceView, which the compositor
            // hands to the display on its own layer — a RenderEffect on that view filters
            // nothing, because the pixels never pass through the view hierarchy's draw. The
            // TextureView that COMPATIBLE uses is an ordinary hardware-layer view, so the
            // shader applies. Every filter in this app depends on it.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(previewView) { engine.bind(lifecycleOwner, previewView, flash) }

    /* ---- grain that moves ---- */

    var seed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(filter, active) {
        if (!filter.animated || !active) return@LaunchedEffect
        // Ten a second. Film grain shifts every frame at 24fps; the eye is satisfied well
        // before that, and each step costs one RenderEffect swap rather than a redraw.
        while (true) {
            seed = Random.nextFloat() * 1000f
            delay(100)
        }
    }

    // The one place the live filter is attached. Keyed on everything the shader reads, so a
    // resize or a filter change rebuilds it and nothing else does.
    LaunchedEffect(filter, seed, frameWidth, frameHeight) {
        previewView.setRenderEffect(
            ShaderRuntime.effectFor(filter, frameWidth, frameHeight, seed),
        )
    }
    DisposableEffect(Unit) { onDispose { previewView.setRenderEffect(null) } }

    /* ---- the wheel ---- */

    // With the exposure strip open the bare turn is exposure, because that is plainly what
    // you opened it to change. Otherwise it is zoom, and holding the wheel in is exposure.
    WheelTurns(active = active && wheelEnabled && !gridOpen && !evOpen, armed = true) { notches ->
        engine.stepZoom(notches)
        vm.showNotice(engine.zoomLabel())
    }
    WheelTurns(active = active && wheelEnabled && evOpen && !gridOpen, armed = true) { notches ->
        engine.stepEv(notches)
    }
    WheelTurns(active = active && wheelEnabled, armed = true, pressed = true) { notches ->
        engine.stepEv(notches)
        vm.showNotice("EV ${engine.evLabel()}")
    }
    WheelTurns(active = active && wheelEnabled && gridOpen, armed = false) { notches ->
        vm.stepFilter(if (notches > 0) 1 else -1)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        /* ---------------- the image ---------------- */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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

            // The only two things allowed on the image besides focus: which way autofocus is
            // set, and a word that is about to disappear.
            Row(
                modifier = Modifier.padding(start = 10.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AfBadge(mode = afMode, state = afState)
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
                if (timer.seconds > 0) {
                    LightText(
                        " ${timer.label}",
                        LightTextVariant.Micro,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            Notice(
                text = notice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }

        /* ---------------- the band ---------------- */

        if (evOpen) {
            ExposureStrip(
                index = ev,
                range = evRange,
                label = engine.evLabel(),
                onStep = { engine.stepEv(it) },
                onReset = { engine.resetEv() },
            )
        }

        if (roll != null) {
            SprocketStrip(offsetFrames = roll?.shot ?: 0)
            RollCounter(
                roll = roll,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .lightClickable { onOpenSettings() },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album, exactly where the stock camera puts it.
            ChromeIcon(
                icon = LightIcons.Album,
                onClick = onOpenRoll,
                lighten = !rollSwipeEnabled,
            )
            ChromeIcon(
                icon = LightIcons.FlipLens,
                onClick = {
                    engine.setLens(
                        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        },
                        flash,
                    )
                },
            )
            // The stock camera's "PHOTO ⌄" slot: what the camera is set to, and a chevron
            // that opens the picker. Here that is the filter, which is the only thing about
            // this camera that has modes.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .lightClickable { gridOpen = true }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightText(
                    text = filter.label.uppercase(),
                    variant = LightTextVariant.Button,
                    align = TextAlign.Center,
                )
                Spacer(Modifier.width(6.dp))
                Canvas(Modifier.width(9.dp).height(6.dp)) {
                    // A chevron, drawn rather than an icon: the SDK's arrow glyphs are all
                    // bigger and heavier than the one next to "PHOTO" on the stock camera.
                    val w = size.width
                    val h = size.height
                    drawLine(
                        colours.content,
                        Offset(0f, 0f),
                        Offset(w / 2f, h),
                        1.4.dp.toPx(),
                        StrokeCap.Round,
                    )
                    drawLine(
                        colours.content,
                        Offset(w, 0f),
                        Offset(w / 2f, h),
                        1.4.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
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
            // Brightness, the stock camera's rightmost control. Exposure compensation is what
            // it means on a camera with no manual controls.
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

        if (shooting) {
            // A hairline that fills while the photograph is being written. The stock camera
            // takes a second or three over a shot and says nothing; this at least admits it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colours.contentSecondary),
            )
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

/**
 * Exposure compensation, as a row of stops.
 *
 * Opened from the brightness icon, and while it is open the bare wheel drives it — that is
 * the whole reason to have a mode rather than a slider: the wheel is a better exposure dial
 * than a thumb on a 3.92" screen ever will be.
 */
@Composable
private fun ExposureStrip(
    index: Int,
    range: IntRange,
    label: String,
    onStep: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val colours = LightThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            "−",
            LightTextVariant.Copy,
            modifier = Modifier
                .lightClickable { onStep(-1) }
                .padding(horizontal = 10.dp),
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
                val stop = range.first + i
                // Whole stops are taller than the thirds between them, so the scale can be
                // read without labels.
                val whole = stop % 3 == 0
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
                .padding(horizontal = 10.dp),
        )
        LightText(
            text = label,
            variant = LightTextVariant.Micro,
            modifier = Modifier.width(34.dp),
            align = TextAlign.End,
        )
    }
}

/**
 * The autofocus badge. `AF-S` or `AF-C`, inverted while the lens is locked.
 *
 * Inversion rather than a colour, because the panel has no colours to spare and LightOS
 * carries state by swapping foreground and background everywhere else too. It is the one
 * permanent mark on the image, and it earns the room: autofocus that gives no sign of being
 * on is autofocus you press twice.
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
 * Tap to focus, swipe sideways for the next filter.
 *
 * Written against [PointerEventPass.Initial] and arbitrating by hand, because the viewfinder
 * sits inside a vertical pager: on the main pass the pager has already claimed the gesture.
 * The axis is decided once, past the slop, and only a horizontal decision is consumed — a
 * vertical drag is left entirely alone so pulling the roll down still feels native.
 */
private fun Modifier.viewfinderGestures(
    enabled: Boolean,
    onTapFocus: (Float, Float) -> Unit,
    onFilterStep: (Int) -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val slopPx = 14.dp.toPx()
        val swipePx = 52.dp.toPx()
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
                onTapFocus(down.position.x, down.position.y)
            }
        }
    },
)
