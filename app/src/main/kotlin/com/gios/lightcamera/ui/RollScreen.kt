package com.gios.lightcamera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.gios.lightcamera.hw.WheelScroll
import com.gios.lightcamera.media.DayLabels
import com.gios.lightcamera.media.Photo
import com.gios.lightcamera.media.RollScope
import com.gios.lightcamera.ui.theme.LightIcon
import com.gios.lightcamera.ui.theme.LightIcons
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import com.gios.lightcamera.ui.theme.lightClickable

/** One row of the flattened roll: either a photo or the day it was taken. */
private sealed interface RollEntry {
    data class Frame(val photo: Photo) : RollEntry
    data class Day(val label: String, val count: Int) : RollEntry
}

/**
 * The roll, hanging above the viewfinder.
 *
 * Laid out **in reverse**, and that is the whole trick. The list is built newest-first and
 * `reverseLayout` puts the head of it at the bottom of the screen, so:
 *
 *  - the photo you took a second ago sits directly against the top edge of the camera,
 *    which is where your eye already is;
 *  - older photographs run upwards, so the roll reads as film coming out of the camera
 *    rather than as a file listing;
 *  - the list's resting position is its own bottom edge, which is precisely where an upward
 *    swipe finds nothing left to scroll and hands the gesture to the pager — so you get back
 *    to the viewfinder from wherever you are, with the same flick every time.
 *
 * Day headings are emitted *after* their photographs for the same reason: reversed, that
 * puts each heading above the group it names.
 */
@Composable
fun RollScreen(
    vm: CameraViewModel,
    active: Boolean,
    mediaGranted: Boolean,
    onRequestMedia: () -> Unit,
    onOpen: (Photo) -> Unit,
    onOpenSettings: () -> Unit,
    onBackToCamera: () -> Unit,
) {
    val colours = LightThemeTokens.colors
    val photos by vm.photos.collectAsState()
    val loading by vm.loadingRoll.collectAsState()
    val scope by vm.prefs.scope.collectAsState()
    val roll by vm.roll.collectAsState()

    val entries = remember(photos) { flatten(photos) }
    val gridState = rememberLazyGridState()
    WheelScroll(gridState, active = active, reverse = true)

    // The roll turns with the phone as well. Opening the photos with the phone already on its side
    // and finding them all sideways is the same complaint as the viewer's, and the same fix — the
    // grid is rotated against the phone rather than the window being unlocked, because unlocking it
    // would let the viewfinder reflow.
    val quarter = rememberDeviceQuarter(active = active)

    RotatedToDevice(quarter) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !mediaGranted -> Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LightText("The roll is your photos.", LightTextVariant.Subheading)
                LightText(
                    "Roll shows the camera roll itself rather than keeping a second album of its own, so it needs to read your photos.",
                    LightTextVariant.Paragraph,
                    lighten = true,
                    modifier = Modifier.padding(top = 10.dp),
                )
                LightText(
                    "ALLOW",
                    LightTextVariant.Button,
                    modifier = Modifier.padding(top = 24.dp).lightClickable { onRequestMedia() },
                )
            }

            entries.isEmpty() && !loading -> EmptyState(
                text = "Nothing on the roll yet.",
                detail = "Swipe up and take a photograph.",
            )

            // **The newest photograph goes bottom right.**
            //
            // `reverseLayout` fills from the bottom, which is what puts the newest frame against
            // the viewfinder — but rows still fill left to right, so the newest landed
            // bottom-*left* and the corner nearest your thumb held the third-newest. Laying the
            // grid out right-to-left fixes it in one line: the head of the list takes the
            // bottom-right cell and the roll fills leftwards and upwards from there, which is
            // also the direction a contact sheet fills.
            else -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 54.dp,
                        bottom = 40.dp,
                        start = 1.dp,
                        end = 1.dp,
                    ),
                ) {
                items(
                    count = entries.size,
                    key = { index ->
                        when (val entry = entries[index]) {
                            is RollEntry.Frame -> entry.photo.id
                            is RollEntry.Day -> "day-${entry.label}"
                        }
                    },
                    span = { index ->
                        if (entries[index] is RollEntry.Day) {
                            GridItemSpan(maxLineSpan)
                        } else {
                            GridItemSpan(1)
                        }
                    },
                ) { index ->
                    when (val entry = entries[index]) {
                        is RollEntry.Day -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Just the day. The count of photographs in it was the sort of
                            // number an interface offers because it happens to know it, not
                            // because anybody wanted it.
                            LightText(entry.label.uppercase(), LightTextVariant.Detail)
                        }

                        is RollEntry.Frame -> Thumb(
                            vm = vm,
                            photo = entry.photo,
                            modifier = Modifier
                                .padding(1.dp)
                                .aspectRatio(1f)
                                .lightClickable { onOpen(entry.photo) },
                        )
                    }
                }
                }
            }
        }

        // Top chrome. Over the oldest photographs on screen rather than the newest, which is
        // the right way round: the header is a label for the screen, not for a photo.
        //
        // `swallowTaps` because a background does not consume touches in Compose: taps on the bar
        // were falling straight through to whichever photograph happened to be underneath, so
        // reaching for settings opened a picture instead.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(colours.scrim)
                .swallowTaps()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText("ROLL", LightTextVariant.Detail)
            Spacer(Modifier.weight(1f))
            LightText(
                text = scope.label.uppercase(),
                variant = LightTextVariant.Superfine,
                lighten = true,
                modifier = Modifier
                    .lightClickable {
                        vm.prefs.setScope(
                            if (scope == RollScope.Camera) {
                                RollScope.Everything
                            } else {
                                RollScope.Camera
                            },
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            ChromeIcon(icon = LightIcons.Settings, lighten = true, onClick = onOpenSettings)
        }

        // Bottom chrome, against the camera. An undeveloped roll lives here, because this is
        // the edge you cross on your way back to the shutter.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colours.scrim),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val loaded = roll
            if (loaded != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText(
                        "ROLL ${loaded.number} · ${loaded.shot} OF ${loaded.length}",
                        LightTextVariant.Superfine,
                    )
                    Spacer(Modifier.weight(1f))
                    LightText(
                        if (loaded.shot == 0) "UNLOAD" else "DEVELOP",
                        LightTextVariant.Superfine,
                        modifier = Modifier.lightClickable { vm.developRoll() },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable { onBackToCamera() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightIcon(LightIcons.Down, size = 9.dp, tint = colours.contentSecondary)
                LightText("  CAMERA", LightTextVariant.Superfine, lighten = true)
            }
        }
    }
    }
}

@Composable
private fun Thumb(vm: CameraViewModel, photo: Photo, modifier: Modifier) {
    val colours = LightThemeTokens.colors
    var image by remember(photo.id) {
        mutableStateOf(vm.thumbs.cached(photo.id)?.asImageBitmap())
    }
    LaunchedEffect(photo.id) {
        if (image != null) return@LaunchedEffect
        image = vm.thumbs.thumbnail(photo.uri, photo.id, THUMB_PX)?.asImageBitmap()
    }
    Box(modifier = modifier.background(colours.rule)) {
        val bitmap: ImageBitmap? = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Photos to entries, newest first, with each day's heading *after* its photographs.
 *
 * Reversed by the layout, that reads as heading-then-photos. Doing it here rather than in the
 * list builder keeps the ordering decision in one place, where it can be reasoned about
 * without also thinking about spans and keys.
 */
private fun flatten(photos: List<Photo>): List<RollEntry> {
    if (photos.isEmpty()) return emptyList()
    val out = ArrayList<RollEntry>(photos.size + 16)
    var day = Long.MIN_VALUE
    var count = 0
    var pending = ArrayList<RollEntry.Frame>()

    fun flush() {
        if (pending.isEmpty()) return
        out += pending
        out += RollEntry.Day(DayLabels.label(day), count)
        pending = ArrayList()
        count = 0
    }

    photos.forEach { photo ->
        val photoDay = DayLabels.dayOf(photo.takenAt)
        if (photoDay != day) {
            flush()
            day = photoDay
        }
        pending += RollEntry.Frame(photo)
        count++
    }
    flush()
    return out
}

private const val THUMB_PX = 256
