package com.gios.lightcamera.ui

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gios.lightcamera.Chrome
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * The viewfinder.
 *
 * The layout is the argument. The frame is a box in the **exact aspect ratio the photo will
 * be saved in**, with black margins above and below it, rather than a preview stretched to
 * the whole panel with the crop implied by a pair of lines. Two things follow from that,
 * both of them the point:
 *
 *  - **What you see is what is saved.** The preview fills the frame box and crops the
 *    overhang; the capture is centre-cropped to the same ratio. There is no third
 *    interpretation of where the edges are.
 *  - **There is somewhere to put the controls.** The margins hold the sprocket strips, the
 *    frame counter and the shutter, so nothing sits on top of the picture. On a 3.92" panel
 *    that is the difference between chrome you can read and chrome you resent.
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
    val scope = rememberCoroutineScope()
    val colours = LightThemeTokens.colors
    val engine = vm.engine

    val filter by vm.filter.collectAsState()
    val aspect by vm.prefs.aspect.collectAsState()
    val chrome by vm.prefs.chrome.collectAsState()
    val flash by vm.prefs.flash.collectAsState()
    val timer by vm.prefs.timer.collectAsState()
    val wheelEnabled by vm.prefs.wheelEnabled.collectAsState()
    val roll by vm.roll.collectAsState()
    val faces by engine.faces.collectAsState()
    val afState by engine.afState.collectAsState()
    val focusPoint by engine.focusPoint.collectAsState()
    val facesSupported by engine.facesSupported.collectAsState()
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
    DisposableEffect(Unit) {
        onDispose { previewView.setRenderEffect(null) }
    }

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

    val facePriority by vm.prefs.facePriority.collectAsState()
    val tilt by rememberTilt(active = active && chrome != Chrome.Clean)
    val priority = remember(faces, frameWidth, frameHeight, facePriority) {
        if (facePriority) FaceMapper.priority(faces, frameWidth, frameHeight) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        /* ---- top deck ---- */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
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
            if (torch) {
                LightText("TORCH", LightTextVariant.Micro, modifier = Modifier.padding(start = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            LightText(
                text = filter.label.uppercase(),
                variant = LightTextVariant.Superfine,
                lighten = filter.agsl == null,
                modifier = Modifier.lightClickable { gridOpen = true },
            )
            Spacer(Modifier.weight(1f))
            ChromeLabel(
                text = aspect.label,
                lighten = true,
                onClick = {
                    val next = com.gios.lightcamera.camera.FrameAspect.entries.let {
                        it[(it.indexOf(aspect) + 1) % it.size]
                    }
                    vm.prefs.setAspect(next)
                    vm.showNotice(next.label)
                },
            )
            ChromeIcon(icon = LightIcons.Settings, lighten = true, onClick = onOpenSettings)
        }

        if (chrome == Chrome.Film) SprocketStrip(offsetFrames = roll?.shot ?: 0)

        /* ---- the frame ---- */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    // Portrait: the ratio inverts, so 4:3 becomes a 3:4 box.
                    .aspectRatio(1f / (aspect.ratio ?: (4f / 3f)))
                    .clipToBounds()
                    .onSizeChanged {
                        frameWidth = it.width
                        frameHeight = it.height
                        vm.onViewSized(it.width, it.height)
                    }
                    .viewfinderGestures(
                        enabled = active,
                        onTapFocus = { x, y ->
                            LightHaptics.click(context)
                            engine.focusAt(x, y, lock = false)
                        },
                        onFilterStep = { vm.stepFilter(it) },
                    ),
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
                FrameOverlay(
                    chrome = chrome,
                    faces = faces,
                    priority = priority,
                    afState = afState,
                    focusPoint = focusPoint,
                    tilt = tilt,
                    facesSupported = facesSupported,
                    modifier = Modifier.fillMaxSize(),
                )
                ShutterBlink(alpha = blink)

                if (countdown != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LightText("$countdown", LightTextVariant.Title)
                    }
                }

                Notice(
                    text = notice,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                )
            }
        }

        if (chrome == Chrome.Film) SprocketStrip(offsetFrames = roll?.shot ?: 0)

        /* ---- bottom deck ---- */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (zoom > 1.02f) {
                    LightText(engine.zoomLabel(), LightTextVariant.Micro)
                } else if (maxZoom > 1.5f) {
                    LightText("1.0x", LightTextVariant.Micro, lighten = true)
                }
                Spacer(Modifier.weight(1f))
                if (timer.seconds > 0) {
                    LightText(timer.label, LightTextVariant.Micro)
                    Spacer(Modifier.width(10.dp))
                }
                if (ev != 0) LightText("EV ${engine.evLabel()}", LightTextVariant.Micro)
            }

            if (roll != null) {
                RollCounter(
                    roll = roll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .lightClickable { onOpenSettings() },
                )
            } else {
                LightText(
                    text = "LOAD A ROLL",
                    variant = LightTextVariant.Micro,
                    lighten = true,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .lightClickable { vm.loadRoll() },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChromeIcon(
                    icon = LightIcons.Grid,
                    onClick = { gridOpen = true },
                    modifier = Modifier.padding(start = 10.dp),
                )
                SoftShutter(
                    shooting = shooting,
                    rollLoaded = roll != null,
                    onHalfPress = { engine.halfPress() },
                    onRelease = { fired ->
                        if (fired) vm.shoot()
                        engine.releaseFocus()
                    },
                )
                ChromeIcon(
                    icon = if (rollSwipeEnabled) LightIcons.FlipLens else LightIcons.Close,
                    lighten = !engine.hasFrontCamera(),
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
                    modifier = Modifier.padding(end = 10.dp),
                )
            }

            if (rollSwipeEnabled) {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .lightClickable { onOpenRoll() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightIcon(
                        icon = LightIcons.Up,
                        size = 9.dp,
                        tint = colours.contentSecondary,
                    )
                    LightText(
                        "  ROLL",
                        LightTextVariant.Micro,
                        lighten = true,
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
            onClose = { gridOpen = false },
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
            .size(74.dp)
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
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = ring),
            )
            val r = (size.minDimension / 2f - 8.dp.toPx()) * inner
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
 * Written against [PointerEventPass.Initial] and arbitrating by hand, because the frame sits
 * inside a vertical pager: on the main pass the pager has already claimed the gesture. The
 * axis is decided once, past the slop, and only a horizontal decision is consumed — a
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
