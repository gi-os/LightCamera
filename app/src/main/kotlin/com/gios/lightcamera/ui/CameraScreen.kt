package com.gios.lightcamera.ui

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gios.lightcamera.Chrome
import com.gios.lightcamera.camera.AfMode
import com.gios.lightcamera.camera.AfState
import com.gios.lightcamera.camera.FaceMapper
import com.gios.lightcamera.camera.FlashMode
import com.gios.lightcamera.camera.FrameAspect
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
 * The viewfinder: the image, edge to edge, and as little else as possible.
 *
 * The Light Phone's own camera is one unbroken picture with a couple of marks on it, and
 * that is the right answer on a 3.92" panel — every pixel of chrome is a pixel of the
 * photograph you can't see. So:
 *
 *  - **The preview fills the screen.** No frame box, no letterbox, no mattes. An earlier
 *    version drew the exact save-aspect as a bordered box with the controls in the margins,
 *    which was honest about cropping and horrible to look through.
 *  - **Chrome floats in the system-bar insets**, over a gradient that fades to nothing before
 *    it reaches the middle of the frame. Against a dark scene it is invisible; against a
 *    bright one it is the only reason the icons are legible.
 *  - **Focus and faces are the only marks on the image**, and they use LightOS's own drawing
 *    — brackets while hunting, a closed box on lock. See [FrameOverlay].
 *
 * The cost of filling the screen is that the sensor is 4:3 and the panel is not, so the file
 * contains a little more than the viewfinder showed. That is how every phone camera works and
 * it is the trade the stock app makes too; the settings screen says so out loud.
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
    val aspect by vm.prefs.aspect.collectAsState()
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
    val maxZoom by engine.maxZoom.collectAsState()
    val ev by engine.ev.collectAsState()
    val torch by engine.torch.collectAsState()
    val notice by vm.notice.collectAsState()
    val countdown by vm.countdown.collectAsState()
    val shooting by vm.shooting.collectAsState()

    var frameWidth by remember { mutableStateOf(0) }
    var frameHeight by remember { mutableStateOf(0) }
    var gridOpen by remember { mutableStateOf(false) }

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

    WheelTurns(active = active && wheelEnabled && !gridOpen, armed = true) { notches ->
        engine.stepZoom(notches)
        vm.showNotice(engine.zoomLabel())
    }
    WheelTurns(active = active && wheelEnabled, armed = true, pressed = true) { notches ->
        engine.stepEv(notches)
        vm.showNotice("EV ${engine.evLabel()}")
    }
    // With the grid open the wheel walks the filters instead — the zoom is not what you are
    // looking at.
    WheelTurns(active = active && wheelEnabled && gridOpen, armed = false) { notches ->
        vm.stepFilter(if (notches > 0) 1 else -1)
    }

    /* ---- the shutter blink ---- */

    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        vm.shutterTick.collect {
            blink = 1f
            // Down in four frames. Long enough to register, short enough that a second
            // photograph is never waiting on it.
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
        /* ---- the image ---- */
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
                        // A buzz for the *ask*. The buzz and beep for the lens actually
                        // landing come off the camera's AF result, in the view model.
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

        /* ---- top chrome ---- */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                )
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                // The one indicator that is always on: which autofocus mode is live, lit up
                // the moment the lens locks. A camera that focuses silently and invisibly is
                // a camera you press twice.
                AfBadge(
                    mode = afMode,
                    state = afState,
                    modifier = Modifier.lightClickable {
                        val next = if (afMode == AfMode.Single) AfMode.Continuous else AfMode.Single
                        vm.prefs.setAfMode(next)
                        vm.showNotice(if (next == AfMode.Single) "AF-S" else "AF-C")
                    },
                )
                if (torch) {
                    LightText(
                        "TORCH",
                        LightTextVariant.Micro,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (zoom > 1.02f) {
                    LightText(engine.zoomLabel(), LightTextVariant.Micro)
                    Spacer(Modifier.width(8.dp))
                }
                if (ev != 0) {
                    LightText("EV ${engine.evLabel()}", LightTextVariant.Micro)
                    Spacer(Modifier.width(8.dp))
                }
                if (timer.seconds > 0) {
                    LightText(timer.label, LightTextVariant.Micro)
                    Spacer(Modifier.width(8.dp))
                }
                ChromeIcon(icon = LightIcons.Settings, lighten = true, onClick = onOpenSettings)
            }
        }

        /* ---- bottom chrome ---- */
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                )
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Notice(text = notice, modifier = Modifier.padding(bottom = 8.dp))

            if (roll != null) {
                SprocketStrip(offsetFrames = roll?.shot ?: 0)
                RollCounter(
                    roll = roll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .lightClickable { onOpenSettings() },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.padding(start = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChromeIcon(icon = LightIcons.Grid, onClick = { gridOpen = true })
                    LightText(
                        text = filter.label.uppercase(),
                        variant = LightTextVariant.Micro,
                        lighten = filter.agsl == null,
                        modifier = Modifier.lightClickable { gridOpen = true },
                    )
                }
                SoftShutter(
                    shooting = shooting,
                    rollLoaded = roll != null,
                    onHalfPress = { engine.halfPress() },
                    onRelease = { fired ->
                        if (fired) vm.shoot()
                        engine.releaseFocus()
                    },
                )
                Column(
                    modifier = Modifier.padding(end = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                    LightText(
                        text = if (aspect == FrameAspect.Full) "4:3" else aspect.label,
                        variant = LightTextVariant.Micro,
                        lighten = true,
                        modifier = Modifier.lightClickable {
                            val all = FrameAspect.entries
                            val next = all[(all.indexOf(aspect) + 1) % all.size]
                            vm.prefs.setAspect(next)
                            vm.showNotice(next.label)
                        },
                    )
                }
            }

            if (rollSwipeEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable { onOpenRoll() }
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightIcon(
                        icon = LightIcons.Up,
                        size = 8.dp,
                        tint = colours.contentSecondary,
                    )
                    LightText("  ROLL", LightTextVariant.Micro, lighten = true)
                }
            }
        }

        // Inside the Box on purpose: as a sibling of it, the stacking order would be at the
        // mercy of whatever layout the pager wraps a page in.
        if (gridOpen) {
            FilterGrid(
                vm = vm,
                previewView = previewView,
                onPick = { id ->
                    vm.setFilter(id)
                    gridOpen = false
                },
                onClose = { gridOpen = false },
            )
        }
    }
}

/**
 * The autofocus badge. `AF-S` or `AF-C`, inverted while the lens is locked.
 *
 * Inversion rather than a colour, because the panel has no colours to spare and LightOS
 * carries state by swapping foreground and background everywhere else too.
 */
@Composable
private fun AfBadge(mode: AfMode, state: AfState, modifier: Modifier = Modifier) {
    val colours = LightThemeTokens.colors
    val locked = state == AfState.Locked
    val label = if (mode == AfMode.Single) "AF-S" else "AF-C"
    Box(
        modifier = modifier
            .background(if (locked) colours.content else Color.Transparent)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        LightText(
            text = label,
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
 * The on-screen shutter, which behaves like the hardware one.
 *
 * Press and it focuses; lift and it fires. Exactly the two-stage release the camera button
 * gives you, so the two controls are the same control and there is nothing extra to learn.
 * A drag off the button cancels, the way a button should.
 */
@Composable
private fun SoftShutter(
    shooting: Boolean,
    rollLoaded: Boolean,
    onHalfPress: () -> Unit,
    onRelease: (fired: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val colours = LightThemeTokens.colors
    val inner by animateFloatAsState(
        targetValue = if (shooting) 0.62f else 1f,
        animationSpec = tween(90),
        label = "shutter",
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    LightHaptics.shutter(context)
                    onHalfPress()
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val offset = change.position
                        if (offset.x < 0f || offset.y < 0f ||
                            offset.x > size.width || offset.y > size.height
                        ) {
                            cancelled = true
                        }
                        if (!change.pressed) break
                    }
                    onRelease(!cancelled)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = 1.6.dp.toPx()
            drawCircle(
                color = colours.content,
                radius = size.minDimension / 2f - ring,
                style = Stroke(width = ring),
            )
            val r = (size.minDimension / 2f - 7.dp.toPx()) * inner
            if (rollLoaded) {
                // A loaded roll gets a square release, so a glance at the shutter tells you
                // the photograph is going onto film and not into the gallery.
                val side = r * 1.62f
                drawRect(
                    color = colours.content,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        (size.width - side) / 2f,
                        (size.height - side) / 2f,
                    ),
                    size = androidx.compose.ui.geometry.Size(side, side),
                )
            } else {
                drawCircle(color = colours.content, radius = r)
            }
        }
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
