package com.gios.lightcamera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gios.lightcamera.CaptureMode
import com.gios.lightcamera.Chrome
import com.gios.lightcamera.Colour
import com.gios.lightcamera.camera.AfState
import com.gios.lightcamera.camera.FaceMapper
import com.gios.lightcamera.camera.FlashMode
import com.gios.lightcamera.filter.FaceQuad
import com.gios.lightcamera.filter.FaceQuads
import com.gios.lightcamera.camera.PuriArt
import com.gios.lightcamera.camera.PuriStrip
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
    var puriOpen by remember { mutableStateOf(false) }
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
    // Purikura is the one filter that needs to know where the faces are, so the effect is rebuilt
    // when they move — which the detector publishes about fifteen times a second. Every other filter
    // keys on nothing that changes, so nothing extra happens for them.
    val faceQuads = if (liveFilter.facesAware) {
        FaceQuads.of(faces, frameWidth, frameHeight)
    } else {
        emptyList()
    }
    val puriFrameId by vm.prefs.puriFrame.collectAsState()
    val puriFaceStickers by vm.prefs.puriFaceStickers.collectAsState()
    val puriMarginStickers by vm.prefs.puriMarginStickers.collectAsState()
    val puriDates by vm.prefs.puriDate.collectAsState()
    val puriStripId by vm.prefs.puriStrip.collectAsState()
    val puriWash by vm.prefs.puriWash.collectAsState()
    val puriSkin by vm.prefs.puriSkin.collectAsState()
    val puriEyes by vm.prefs.puriEyes.collectAsState()
    val puriChin by vm.prefs.puriChin.collectAsState()
    val puriSlim by vm.prefs.puriSlim.collectAsState()
    val puriSeed by vm.puriSeed.collectAsState()
    // Which way up the photograph will be, from the same number the shutter uses.
    val turn by vm.engine.previewRotation.collectAsState()
    LaunchedEffect(
        liveFilter,
        seed,
        frameWidth,
        frameHeight,
        faceQuads,
        puriWash,
        puriSkin,
        puriEyes,
        puriChin,
        puriSlim,
        turn,
    ) {
        previewView.setRenderEffect(
            ShaderRuntime.effectFor(
                filter = liveFilter,
                width = frameWidth,
                height = frameHeight,
                seed = seed,
                faces = faceQuads,
                // The preview image is still in the panel's frame, so the shader needs to know how the
                // face is lying in it. The captured photograph is turned upright before the shader sees
                // it, which is why the shutter passes no turn at all.
                tune = vm.prefs.puriTune(turns = turn / 90),
            ),
        )
    }
    DisposableEffect(Unit) { onDispose { previewView.setRenderEffect(null) } }

    /* ---- the wheel ---- */

    // **A bare turn walks the filters**, grid open or not — that is what the wheel is for on this
    // camera. The phone has no optical zoom and the stock app offers none, so a dial spent on
    // digital crop was a dial spent on nothing; a dial that changes what the photograph looks
    // like earns every notch. Unarmed, because each notch has to count, and None is three notches
    // wide on the track so a stray one lands somewhere harmless.
    // **Nothing scrolls while the Purikura menu is open.** The menu is a list of five things you are
    // reading; a wheel that walked the filters underneath it would change the picture behind the menu
    // and take Purikura away, closing the menu you were using.
    WheelTurns(active = active && wheelEnabled && !evOpen && !puriOpen, armed = false) { notches ->
        vm.stepFilter(if (notches > 0) 1 else -1)
    }
    // Exposure keeps both of its routes: the strip while it is open, and hold-and-turn always.
    WheelTurns(active = active && wheelEnabled && evOpen && !puriOpen, armed = true) { notches ->
        engine.stepEv(notches)
    }
    WheelTurns(active = active && wheelEnabled && !puriOpen, armed = true, pressed = true) { notches ->
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

    val levelOn by vm.prefs.level.collectAsState()
    val tilt by rememberTilt(active = active && levelOn)
    val levelVisible = rememberLevelVisible(tilt, enabled = active && levelOn)
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
                        // The Purikura chip, and only while Purikura is on. It opens the menu rather
                        // than stepping the frame: there are fourteen frames, two kinds of sticker, a
                        // date and a strip layout behind it, and a chip that cycled one of those and
                        // hid the rest would be a worse version of both.
                        if (liveFilter.facesAware) {
                            Row(
                                modifier = Modifier
                                    .lightClickable { puriOpen = !puriOpen }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LightText(
                                    text = "PURI",
                                    variant = LightTextVariant.Button,
                                    align = TextAlign.Center,
                                )
                                Spacer(Modifier.width(7.dp))
                                Chevron(pointingUp = puriOpen)
                            }
                            Spacer(Modifier.weight(1f))
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
                            enabled = active && !puriOpen,
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

                // **The frame, the stickers and the date, live.** Rendered by the same
                // `PuriArt.draw` the shutter calls, from the same seed, so this is not an
                // impression of the photograph — it is the photograph's furniture, drawn once into a
                // bitmap and laid over the preview.
                //
                // Half resolution, because it is redrawn whenever a face moves and a full-panel
                // ARGB bitmap fifteen times a second is not a thing to do to a phone. Everything in
                // it is vector work scaled from the short edge, so scaling the result back up costs
                // a little softness on a hairline and nothing else.
                //
                // The face positions are quantised to fiftieths before they key the redraw, or the
                // detector's jitter alone would rebuild this constantly while nothing visibly moved.
                if (liveFilter.facesAware && frameWidth > 0 && frameHeight > 0) {
                    val settled = faceQuads.map { q ->
                        listOf(q.cx, q.cy, q.hw, q.hh).map { (it * 50f).toInt() }
                    }
                    // **Drawn the way up the photograph will be, then turned back to face you.**
                    // Hold the phone sideways and the file comes out landscape, so the frame's bands
                    // run along its long edges and the date reads horizontally across the bottom of
                    // it. Drawing the overlay in the panel's portrait space instead would put the date
                    // up the side of the finished photograph — and, worse, would show you one thing
                    // and save another.
                    val sideways = turn == 90 || turn == 270
                    val overlay = remember(
                        puriFrameId,
                        puriSeed,
                        puriFaceStickers,
                        puriMarginStickers,
                        puriDates,
                        settled,
                        frameWidth,
                        frameHeight,
                        turn,
                    ) {
                        // Half resolution: this is redrawn whenever a face moves, and a full-panel
                        // ARGB bitmap fifteen times a second is not a thing to do to a phone.
                        val half = { n: Int -> (n / 2).coerceAtLeast(1) }
                        val ow = if (sideways) half(frameHeight) else half(frameWidth)
                        val oh = if (sideways) half(frameWidth) else half(frameHeight)
                        val bitmap = createBitmap(ow, oh)
                        PuriArt.draw(
                            canvas = AndroidCanvas(bitmap),
                            w = ow,
                            h = oh,
                            // Random resolved from the same seed the shutter will use, so the frame
                            // you are looking at is the frame you are about to get.
                            frame = PuriArt.resolveFrame(puriFrameId, puriSeed),
                            plan = PuriArt.plan(
                                seed = puriSeed,
                                // Faces are in panel space, so they turn with everything else.
                                faces = faceQuads.map { FaceQuads.rotated(it, turn) },
                                faceStickers = puriFaceStickers,
                                marginStickers = puriMarginStickers,
                                dateId = puriDates,
                            ),
                            millis = System.currentTimeMillis(),
                        )
                        bitmap.asImageBitmap()
                    }
                    // Undo the turn for display: the photograph will be rotated by `turn`, so showing
                    // the same thing on an unrotated panel means rotating the overlay the other way.
                    RotatedToDevice((360 - turn) % 360, opaque = false) {
                        Image(
                            bitmap = overlay,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                FrameOverlay(
                    chrome = chrome,
                    faces = faces,
                    priority = priority,
                    afState = afState,
                    focusPoint = focusPoint,
                    tilt = tilt,
                    levelVisible = levelVisible,
                    turn = turn,
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

                // **Only what is abnormal.** The `AF-S` badge and the filter name are gone: the
                // focus mark already says what focus is doing, and the picture already shows what
                // the filter is doing. A label naming a thing you can see is a label in the way.
                //
                // What is left is state you could not otherwise know: that it is recording, that
                // the torch is on, that the lens is zoomed, that exposure is pushed, that a timer
                // is armed. Each disappears the moment it goes back to normal.
                Row(
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (recording) {
                        RecordDot()
                        LightText(
                            " ${"%d:%02d".format(recordSeconds / 60, recordSeconds % 60)}",
                            LightTextVariant.Detail,
                        )
                    }
                    if (torch) {
                        LightText(
                            " TORCH",
                            LightTextVariant.Detail,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (zoom > 1.02f) {
                        LightText(
                            " ${engine.zoomLabel()}",
                            LightTextVariant.Detail,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (ev != 0) {
                        LightText(
                            " EV ${engine.evLabel()}",
                            LightTextVariant.Detail,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (timer.seconds > 0 && mode != CaptureMode.Video) {
                        LightText(
                            " ${timer.label}",
                            LightTextVariant.Detail,
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
        if (puriOpen) {
            PuriMenu(
                seed = puriSeed,
                frameId = puriFrameId,
                faceStickers = puriFaceStickers,
                marginStickers = puriMarginStickers,
                dateId = puriDates,
                stripId = puriStripId,
                onFrame = { vm.prefs.setPuriFrame(it) },
                onFaceStickers = { vm.prefs.setPuriFaceStickers(!puriFaceStickers) },
                onMarginStickers = { vm.prefs.setPuriMarginStickers(!puriMarginStickers) },
                onDate = { vm.prefs.setPuriDate(it) },
                onStrip = { vm.prefs.setPuriStrip(it) },
                wash = puriWash,
                skin = puriSkin,
                eyes = puriEyes,
                chin = puriChin,
                slim = puriSlim,
                onWash = { vm.prefs.setPuriWash(!puriWash) },
                onSkin = { vm.prefs.setPuriSkin(!puriSkin) },
                onEyes = { vm.prefs.setPuriEyes(!puriEyes) },
                onChin = { vm.prefs.setPuriChin(!puriChin) },
                onSlim = { vm.prefs.setPuriSlim(!puriSlim) },
                onClose = { puriOpen = false },
            )
        }

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
                            variant = LightTextVariant.Detail,
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
 * Everything a Purikura is made of, on one screen, with a sample of it beside the rows.
 *
 * **The whole screen, not a strip beside the band.** A menu you have to scroll on a 3.92" screen held
 * sideways is a menu that hides half its options, so this covers the viewfinder while it is open and
 * nothing scrolls underneath it.
 *
 * Frame, Date, Four-shot and Look are **lists**, not values you cycle: fourteen frames and eight dates
 * are too many to walk one tap at a time, the first item in each is Random, and every row in a list
 * carries a thumbnail of what it does — which is the only way to choose between fourteen borders whose
 * names are one word each.
 *
 * Look is where the effect itself lives, five switches deep: the wash, the skin, the eyes, the chin, the
 * slimming. They are separate because they fail separately — the chin and the slimming are the two that
 * look uncanny on a face the detector has boxed slightly wrong, and you should be able to drop those
 * without losing the eyes.
 */
@Composable
private fun PuriMenu(
    seed: Long,
    frameId: String,
    faceStickers: Boolean,
    marginStickers: Boolean,
    dateId: String,
    stripId: String,
    wash: Boolean,
    skin: Boolean,
    eyes: Boolean,
    chin: Boolean,
    slim: Boolean,
    onFrame: (String) -> Unit,
    onFaceStickers: () -> Unit,
    onMarginStickers: () -> Unit,
    onDate: (String) -> Unit,
    onStrip: (String) -> Unit,
    onWash: () -> Unit,
    onSkin: () -> Unit,
    onEyes: () -> Unit,
    onChin: () -> Unit,
    onSlim: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = LightThemeTokens.colors
    var picking by remember { mutableStateOf<String?>(null) }
    val strip = if (PuriStrip.enabled(stripId)) PuriStrip.resolveLayout(stripId, seed) else null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colours.background)
            // Eats every touch: the swipe down to the roll is a drag on a pager two levels up, and a
            // background does not stop one.
            .swallowTaps(),
    ) {
        HeldSideways {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LightText(
                            text = when (picking) {
                                "frame" -> "FRAME"
                                "date" -> "DATE"
                                "strip" -> "FOUR-SHOT"
                                "look" -> "LOOK"
                                else -> "PURIKURA"
                            },
                            variant = LightTextVariant.Detail,
                        )
                        Spacer(Modifier.weight(1f))
                        ChromeIcon(
                            icon = LightIcons.Close,
                            lighten = true,
                            onClick = { if (picking != null) picking = null else onClose() },
                        )
                    }
                    when (picking) {
                        "frame" -> PuriPicker(
                            options = listOf(PuriArt.RANDOM to "Random") +
                                PuriArt.frames.map { it.id to it.label },
                            chosen = frameId,
                            onPick = { onFrame(it); picking = null },
                            thumbnail = { id, w, h ->
                                puriTile(w, h, PuriArt.resolveFrame(id, seed), PuriArt.OFF, seed, false, false)
                            },
                        )

                        "date" -> PuriPicker(
                            options = listOf(PuriArt.RANDOM to "Random", PuriArt.OFF to "Off") +
                                PuriArt.dates.map { it.id to it.label },
                            chosen = dateId,
                            onPick = { onDate(it); picking = null },
                            thumbnail = { id, w, h ->
                                puriTile(w, h, PuriArt.frameById("none"), id, seed, false, false)
                            },
                        )

                        "strip" -> PuriPicker(
                            options = listOf(PuriStrip.OFF to "Off", PuriArt.RANDOM to "Random") +
                                PuriStrip.layouts.drop(1).map { it.id to it.label },
                            chosen = stripId,
                            onPick = { onStrip(it); picking = null },
                            thumbnail = { id, w, h ->
                                if (id == PuriStrip.OFF) {
                                    puriTile(w, h, PuriArt.resolveFrame(frameId, seed), PuriArt.OFF, seed, false, false)
                                } else {
                                    puriStripTile(
                                        w,
                                        h,
                                        PuriStrip.resolveLayout(id, seed),
                                        PuriArt.resolveFrame(frameId, seed),
                                        seed,
                                    )
                                }
                            },
                        )

                        "look" -> Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        ) {
                            PuriRow("Pink wash", if (wash) "On" else "Off", onWash)
                            PuriRow("Skin", if (skin) "On" else "Off", onSkin)
                            PuriRow("Bigger eyes", if (eyes) "On" else "Off", onEyes)
                            PuriRow("Narrow chin", if (chin) "On" else "Off", onChin)
                            PuriRow("Smaller face", if (slim) "On" else "Off", onSlim)
                            LightText(
                                "The wash is the pink, the blow-out and the glitter. Without it you get the smoothing and the shaping, which is a beauty filter rather than a booth print.",
                                LightTextVariant.Superfine,
                                lighten = true,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }

                        else -> {
                            PuriRow(
                                "Frame",
                                labelFor(frameId, PuriArt.frames.map { it.id to it.label }),
                            ) { picking = "frame" }
                            PuriRow("Face stickers", if (faceStickers) "On" else "Off", onFaceStickers)
                            PuriRow(
                                "Margin stickers",
                                if (marginStickers) "On" else "Off",
                                onMarginStickers,
                            )
                            PuriRow(
                                "Date",
                                labelFor(dateId, PuriArt.dates.map { it.id to it.label }),
                            ) { picking = "date" }
                            PuriRow(
                                "Four-shot",
                                labelFor(stripId, PuriStrip.layouts.map { it.id to it.label }),
                            ) { picking = "strip" }
                            PuriRow("Look", "${listOf(wash, skin, eyes, chin, slim).count { it }} of 5") {
                                picking = "look"
                            }
                            Spacer(Modifier.weight(1f))
                            LightText(
                                text = if (strip != null) {
                                    "Four shots, three seconds apart. The strip goes on the roll; the frames are kept behind it."
                                } else {
                                    "Random is chosen fresh for each photograph."
                                },
                                variant = LightTextVariant.Superfine,
                                lighten = true,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                PuriSample(
                    seed = seed,
                    frame = PuriArt.resolveFrame(frameId, seed),
                    faceStickers = faceStickers,
                    marginStickers = marginStickers,
                    dateId = dateId,
                    strip = strip,
                )
            }
        }
    }
}

/** "Random", "Off", or the label of whatever was chosen. */
private fun labelFor(id: String, options: List<Pair<String, String>>): String = when (id) {
    PuriArt.RANDOM -> "Random"
    PuriArt.OFF -> "Off"
    else -> options.firstOrNull { it.first == id }?.second ?: "Random"
}

/**
 * One cell of the stand-in: a grey head and shoulders with the furniture drawn on it.
 *
 * The same call the photograph makes, at the size of a postage stamp. That is the whole reason the
 * thumbnails are worth having — they are not illustrations of the frames, they are the frames.
 */
private fun puriTile(
    w: Int,
    h: Int,
    frame: PuriArt.Frame,
    dateId: String,
    seed: Long,
    faceStickers: Boolean,
    marginStickers: Boolean,
): android.graphics.Bitmap {
    val bitmap = createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(0x3A, 0x3A, 0x38))
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.rgb(0x8C, 0x86, 0x80)
    }
    canvas.drawCircle(w * 0.5f, h * 0.4f, w * 0.22f, paint)
    canvas.drawOval(w * 0.16f, h * 0.66f, w * 0.84f, h * 1.3f, paint)
    PuriArt.draw(
        canvas = canvas,
        w = w,
        h = h,
        frame = frame,
        plan = PuriArt.plan(
            seed = seed,
            faces = listOf(FaceQuad(cx = 0.5f, cy = 0.4f, hw = 0.22f, hh = 0.165f)),
            faceStickers = faceStickers,
            marginStickers = marginStickers,
            dateId = dateId,
        ),
        millis = System.currentTimeMillis(),
    )
    return bitmap
}

/** Four tiles, run through the real strip composer, so a layout row shows its own layout. */
private fun puriStripTile(
    w: Int,
    h: Int,
    layout: PuriStrip.Layout,
    frame: PuriArt.Frame,
    seed: Long,
): android.graphics.Bitmap {
    val cellH = (h / PuriStrip.SHOTS).coerceAtLeast(8)
    val cellW = (cellH * 3 / 4).coerceAtLeast(6)
    val cells = (0 until PuriStrip.SHOTS).map {
        puriTile(
            cellW,
            cellH,
            if (layout.outerFrame) PuriArt.frameById("none") else frame,
            PuriArt.OFF,
            seed + it * 977L,
            false,
            false,
        )
    }
    val sheet = PuriStrip.compose(cells, layout, frame, System.currentTimeMillis())
    cells.forEach { it.recycle() }
    return sheet ?: puriTile(w, h, frame, PuriArt.OFF, seed, false, false)
}

/**
 * A list of options, each with a thumbnail of itself and the current one filled in.
 *
 * Filled rather than ticked: there is no tick in the icon set, and an inverted row reads at a glance in a
 * way a small mark beside text does not.
 */
@Composable
private fun PuriPicker(
    options: List<Pair<String, String>>,
    chosen: String,
    onPick: (String) -> Unit,
    thumbnail: (String, Int, Int) -> android.graphics.Bitmap,
) {
    val colours = LightThemeTokens.colors
    val density = LocalDensity.current
    val tileW = with(density) { 26.dp.roundToPx() }
    val tileH = with(density) { 35.dp.roundToPx() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        options.forEach { (id, label) ->
            val here = id == chosen
            // Random has nothing of its own to show, so it borrows whatever the seed currently says.
            val tile = remember(id, chosen == id, tileW, tileH) {
                runCatching { thumbnail(id, tileW, tileH).asImageBitmap() }.getOrNull()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable { onPick(id) }
                    .background(if (here) colours.content else Color.Transparent)
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tile != null) {
                    Image(
                        bitmap = tile,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(26.dp).height(35.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                }
                LightText(
                    text = label,
                    variant = LightTextVariant.Copy,
                    color = if (here) colours.background else colours.content,
                )
            }
        }
    }
}

/**
 * A thumbnail of what you are about to get, as large as the panel allows.
 *
 * A stand-in rather than the live viewfinder, because the point of it is the *furniture* and a moving
 * picture behind a menu is a distraction. With a [strip] it builds four cells and runs them through the
 * real `PuriStrip.compose`, so the sample is not an illustration of a strip — it is one. A strip is 1:4,
 * so it gets the full height of the panel and takes whatever width that leaves: at a fixed size it came
 * out the width of a fingernail.
 */
@Composable
private fun PuriSample(
    seed: Long,
    frame: PuriArt.Frame,
    faceStickers: Boolean,
    marginStickers: Boolean,
    dateId: String,
    strip: PuriStrip.Layout?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cellW = with(density) { 150.dp.roundToPx() }
    val cellH = with(density) { 200.dp.roundToPx() }

    val sample = remember(seed, frame.id, faceStickers, marginStickers, dateId, strip?.id, cellW, cellH) {
        if (strip == null) {
            puriTile(cellW, cellH, frame, dateId, seed, faceStickers, marginStickers).asImageBitmap()
        } else {
            val cells = (0 until PuriStrip.SHOTS).map {
                puriTile(
                    cellW,
                    cellH,
                    // One border round the whole strip means none on the cells inside it.
                    if (strip.outerFrame) PuriArt.frameById("none") else frame,
                    // The date goes on the print, once, not into all four panels.
                    PuriArt.OFF,
                    // A different seed per cell: a strip's panels are decorated separately, exactly as
                    // the shutter does it.
                    seed + it * 977L,
                    faceStickers,
                    marginStickers,
                )
            }
            val sheet = PuriStrip.compose(cells, strip, frame, System.currentTimeMillis())
            cells.forEach { it.recycle() }
            // No date anywhere on a strip. Four copies down the panels was wrong, and one in the margin
            // was still a date on something that is already stamped by being four photographs of one
            // moment — the layouts that want a printed date have their own footer.
            sheet?.asImageBitmap()
                ?: puriTile(cellW, cellH, frame, dateId, seed, faceStickers, marginStickers).asImageBitmap()
        }
    }

    // **A bounded width, and that is not cosmetic.** `ContentScale.Fit` inside a column with no width
    // constraint takes the bitmap's intrinsic width, so the 2x2 sheet — twice as wide as a single frame —
    // shoved the rows off the side of the screen. Full height, fixed width, and every layout fits inside
    // it: a strip lands tall and narrow, the grid lands square.
    Column(
        modifier = modifier.fillMaxHeight().width(124.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = sample,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        LightText(
            text = "EXAMPLE",
            variant = LightTextVariant.Micro,
            lighten = true,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun PuriRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(label, LightTextVariant.Copy)
        Spacer(Modifier.weight(1f))
        LightText(value, LightTextVariant.Copy, lighten = true)
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
                    variant = LightTextVariant.Superfine,
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
