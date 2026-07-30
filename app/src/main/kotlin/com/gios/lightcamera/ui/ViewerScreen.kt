package com.gios.lightcamera.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gios.lightcamera.hw.WheelTurns
import com.gios.lightcamera.media.Photo
import com.gios.lightcamera.ui.theme.LightIcons
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import com.gios.lightcamera.ui.theme.lightClickable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One photograph, full screen.
 *
 * The wheel steps between frames, which is the same gesture as advancing film and reads
 * better than a swipe once there are more than a few photographs to get through. Chrome
 * hides on a tap, because the reason you opened this was to look at the picture.
 */
@Composable
fun ViewerScreen(
    vm: CameraViewModel,
    initial: Photo,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colours = LightThemeTokens.colors
    val photos by vm.photos.collectAsState()

    val startIndex = remember(initial.id, photos) {
        photos.indexOfFirst { it.id == initial.id }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = startIndex, pageCount = { photos.size })
    var chromeVisible by remember { mutableStateOf(true) }

    val trash = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { scope.launch { vm.refreshRoll() } }

    WheelTurns(active = true, armed = true) { notches ->
        scope.launch {
            val next = (pager.currentPage - notches).coerceIn(0, (photos.size - 1).coerceAtLeast(0))
            pager.animateScrollToPage(next)
        }
    }

    // Every photograph gone means there is nothing left to look at.
    LaunchedEffect(photos.size) { if (photos.isEmpty()) onClose() }

    // A photograph is the one thing on this phone that is definitely worth seeing in colour.
    val colour by vm.prefs.colour.collectAsState()
    ColourEffect(enabled = colour != com.gios.lightcamera.Colour.Off)

    // Decode no bigger than the panel. A 12MP JPEG at 1:1 is 48MB of heap for a 1080px view.
    val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .lightClickable(haptics = false) { chromeVisible = !chromeVisible },
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val photo = photos.getOrNull(page) ?: return@HorizontalPager
            var image by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(photo.id) {
                image = vm.thumbs.frame(photo.uri, photo.id, screenWidthPx)?.asImageBitmap()
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val bitmap = image
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = photo.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (chromeVisible) {
            val photo = photos.getOrNull(pager.currentPage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(colours.scrim)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeIcon(icon = LightIcons.Close, onClick = onClose)
                Spacer(Modifier.weight(1f))
                if (photo != null) {
                    LightText(
                        text = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
                            .format(Date(photo.takenAt)),
                        variant = LightTextVariant.Micro,
                        lighten = true,
                    )
                }
                Spacer(Modifier.weight(1f))
                LightText(
                    text = "${pager.currentPage + 1}/${photos.size}",
                    variant = LightTextVariant.Micro,
                    lighten = true,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colours.scrim)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeIcon(
                    icon = LightIcons.Trash,
                    onClick = {
                        val target = photos.getOrNull(pager.currentPage) ?: return@ChromeIcon
                        val sender = vm.trashRequest(target)
                        if (sender != null) {
                            trash.launch(IntentSenderRequest.Builder(sender).build())
                        } else {
                            vm.showNotice("Can't bin that one")
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
                // The send button is off unless you have pointed it at LightChat. A share
                // sheet is the one place a Light Phone stops feeling like a Light Phone — a
                // grid of every app that ever registered for an image, on a phone whose whole
                // argument is that there aren't any. Switched on, it has one destination and
                // no chooser.
                val sendEnabled by vm.prefs.sendToLightChat.collectAsState()
                ChromeIcon(
                    icon = LightIcons.Share,
                    lighten = !sendEnabled,
                    onClick = {
                        if (!sendEnabled) {
                            vm.showNotice("Turn on Send to LightChat in settings")
                            return@ChromeIcon
                        }
                        val target = photos.getOrNull(pager.currentPage) ?: return@ChromeIcon
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, target.uri)
                            // The package is named explicitly, which is what makes this a link
                            // to LightChat rather than a share sheet with LightChat in it.
                            setPackage(LIGHT_CHAT)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        // Resolve before starting: an explicit-package intent nothing can
                        // handle throws ActivityNotFound, and "LightChat can't take images" is
                        // a far more useful thing to be told than a crash.
                        val resolves = runCatching {
                            context.packageManager.resolveActivity(send, 0) != null
                        }.getOrDefault(false)
                        if (!resolves) {
                            vm.showNotice("LightChat can't receive photos")
                            return@ChromeIcon
                        }
                        runCatching { context.startActivity(send) }
                            .onFailure { vm.showNotice("LightChat wouldn't open") }
                    },
                )
            }
        }
    }
}

/** Giovanni's iMessage client. The only destination the send button has. */
private const val LIGHT_CHAT = "com.gios.lightchat"

/**
 * A developed roll, all at once.
 *
 * The only screen in the app that exists purely for a moment: twenty-four photographs you
 * have not seen, laid out as a contact sheet with their frame numbers, which is what
 * developing a roll ought to feel like. Dismissed and never shown again — from then on they
 * are just photographs on the roll like any others.
 */
@Composable
fun ContactSheet(
    vm: CameraViewModel,
    developed: com.gios.lightcamera.roll.FilmRoll.DevelopedRoll,
    onClose: () -> Unit,
) {
    val colours = LightThemeTokens.colors
    val photos by vm.photos.collectAsState()
    val frames = remember(developed, photos) {
        val wanted = developed.uris.toSet()
        photos.filter { it.uri in wanted }.sortedBy { it.takenAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                "ROLL ${developed.number} DEVELOPED",
                LightTextVariant.Superfine,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            ChromeIcon(icon = LightIcons.Accept, onClick = onClose)
        }

        if (frames.isEmpty()) {
            EmptyState(
                text = "Nothing came out.",
                detail = "The frames couldn't be written to the camera roll.",
            )
            return@Column
        }

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
        ) {
            items(count = frames.size, key = { frames[it].id }) { index ->
                val photo = frames[index]
                Column(
                    modifier = Modifier.padding(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var image by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(photo.id) {
                        image = vm.thumbs.thumbnail(photo.uri, photo.id, 256)?.asImageBitmap()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colours.rule)
                            .padding(0.dp),
                    ) {
                        val bitmap = image
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = photo.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp),
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().height(96.dp))
                        }
                    }
                    LightText(
                        text = "%02d".format(index + 1),
                        variant = LightTextVariant.Micro,
                        lighten = true,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
