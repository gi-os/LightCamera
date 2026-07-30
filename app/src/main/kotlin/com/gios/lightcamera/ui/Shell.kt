package com.gios.lightcamera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gios.lightcamera.media.Photo
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.lightClickable
import kotlinx.coroutines.launch

private const val PAGE_ROLL = 0
private const val PAGE_CAMERA = 1

/**
 * The whole app, which is two pages stacked vertically.
 *
 * **The roll sits above the viewfinder.** Pulling down on the camera brings it into view,
 * the way pulling down on a window blind brings the blind down: the photographs you have
 * already taken are behind the phone's top edge, and the gesture that reveals them is the
 * gesture that would physically move them into sight.
 *
 * That geometry is also why the grid is laid out in reverse ([RollScreen]). The newest photo
 * hangs immediately above the viewfinder and older ones run further up, so the roll is a
 * strip of film coming out of the camera and the resting position of the list is its bottom
 * edge — which is exactly where an upward swipe has nothing left to scroll and hands the
 * gesture back to the pager. Lay it out the usual way and the only route back to the camera
 * is scrolling to the end of your entire photo library.
 */
@Composable
fun Shell(
    vm: CameraViewModel,
    /** True when another app asked for a single photo; there is no roll to browse then. */
    captureRequest: Boolean,
) {
    HeldSideways { ShellContent(vm = vm, captureRequest = captureRequest) }
}

/**
 * The whole app, turned a quarter clockwise inside a portrait window.
 *
 * This is how LightOS's own camera works and it takes a moment to see why. The window is
 * locked to portrait — the app never rotates, there is no landscape layout, nothing reflows —
 * and the interface is *drawn* rotated inside it. Turn the phone anticlockwise, the way you
 * would pick up a compact camera, and everything is upright: the picture fills the width, the
 * control band is along the bottom, and the phone's camera key has come round to the top edge
 * exactly where a shutter release belongs. Held in portrait, that same band runs down the
 * left-hand side.
 *
 * Rotating rather than supporting landscape is the right call and not a shortcut:
 *
 *  - **Nothing moves while you shoot.** A layout that reflowed on orientation would swap the
 *    band from one edge to another as you turned the camera to frame something, which is a
 *    viewfinder fighting you.
 *  - **Every spatial idea survives.** The roll is still *above* the viewfinder and still
 *    arrives with a downward pull, because "down" in here is down in your hand when the phone
 *    is held the way the app expects.
 *  - **The framing comes out right for free.** A 4:3 sensor very nearly fills a screen turned
 *    on its side, so almost nothing is cropped away — whereas portrait wastes a third of it.
 *
 * The mechanics: a box of the parent's *swapped* dimensions, escaping the incoming constraints
 * with `requiredSize`, centred, and turned 90°. Rotated about its own centre it covers the
 * parent exactly. Compose maps pointer input back through the layer, so touches land where
 * they look like they should, and an `AndroidView` inside — the camera preview — is drawn into
 * the same layer and turns with it.
 */
@Composable
private fun HeldSideways(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val across = maxHeight
        val down = maxWidth
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(width = across, height = down)
                .graphicsLayer { rotationZ = 90f },
        ) {
            content()
        }
    }
}

@Composable
private fun ShellContent(vm: CameraViewModel, captureRequest: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var mediaGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] ?: cameraGranted
        mediaGranted = result[Manifest.permission.READ_MEDIA_IMAGES] ?: mediaGranted
        if (mediaGranted) vm.onPermissionsChanged()
    }

    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (!cameraGranted) add(Manifest.permission.CAMERA)
            if (!mediaGranted) add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        if (wanted.isNotEmpty()) ask.launch(wanted.toTypedArray())
    }

    LaunchedEffect(mediaGranted) {
        if (mediaGranted) vm.startObservingMedia()
    }

    // "Whole app" holds colour for the roll as well, which is what you want if you are
    // looking through photographs rather than taking them.
    val colour by vm.prefs.colour.collectAsState()
    ColourEffect(enabled = colour == com.gios.lightcamera.Colour.Always)

    if (!cameraGranted) {
        Refusal(
            "Roll needs the camera.",
            "Grant it and the viewfinder appears.",
            onRetry = { ask.launch(arrayOf(Manifest.permission.CAMERA)) },
        )
        return
    }

    var viewing by remember { mutableStateOf<Photo?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    val pager = rememberPagerState(initialPage = PAGE_CAMERA, pageCount = { 2 })

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (captureRequest) {
            // One photo for somebody else. No roll, no settings, no way to wander off.
            CameraScreen(
                vm = vm,
                active = true,
                onOpenRoll = {},
                onOpenSettings = {},
                rollSwipeEnabled = false,
            )
        } else {
            VerticalPager(
                state = pager,
                pageSize = PageSize.Fill,
                // Keep both pages composed: the camera takes a few hundred milliseconds to
                // rebind, and a viewfinder that has to warm up every time you glance at the
                // roll is a viewfinder you stop trusting to be ready.
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_ROLL -> RollScreen(
                        vm = vm,
                        active = pager.currentPage == PAGE_ROLL,
                        mediaGranted = mediaGranted,
                        onRequestMedia = { ask.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES)) },
                        onOpen = { viewing = it },
                        onOpenSettings = { settingsOpen = true },
                        onBackToCamera = {
                            scope.launch { pager.animateScrollToPage(PAGE_CAMERA) }
                        },
                    )

                    else -> CameraScreen(
                        vm = vm,
                        active = pager.currentPage == PAGE_CAMERA && viewing == null && !settingsOpen,
                        onOpenRoll = { scope.launch { pager.animateScrollToPage(PAGE_ROLL) } },
                        onOpenSettings = { settingsOpen = true },
                        rollSwipeEnabled = true,
                    )
                }
            }
        }

        AnimatedVisibility(visible = viewing != null, enter = fadeIn(), exit = fadeOut()) {
            val photo = viewing
            if (photo != null) {
                ViewerScreen(
                    vm = vm,
                    initial = photo,
                    onClose = { viewing = null },
                )
            }
        }

        AnimatedVisibility(visible = settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            SettingsScreen(vm = vm, onClose = { settingsOpen = false })
        }

        val developed by vm.developed.collectAsState()
        AnimatedVisibility(visible = developed != null, enter = fadeIn(), exit = fadeOut()) {
            val result = developed
            if (result != null) {
                ContactSheet(vm = vm, developed = result, onClose = { vm.dismissDeveloped() })
            }
        }
    }
}

@Composable
private fun Refusal(title: String, detail: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightText(title, LightTextVariant.Subheading, align = TextAlign.Center)
        LightText(
            detail,
            LightTextVariant.Paragraph,
            lighten = true,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        LightText(
            "ASK AGAIN",
            LightTextVariant.Button,
            modifier = Modifier
                .padding(top = 28.dp)
                .lightClickable { onRetry() },
        )
    }
}
