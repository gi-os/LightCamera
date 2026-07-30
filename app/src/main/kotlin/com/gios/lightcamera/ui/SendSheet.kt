package com.gios.lightcamera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gios.lightcamera.media.Photo
import com.gios.lightcamera.send.ContactsRepo
import com.gios.lightcamera.send.Handoff
import com.gios.lightcamera.send.Recipient
import com.gios.lightcamera.send.Recipients
import com.gios.lightcamera.ui.theme.LightIcons
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import com.gios.lightcamera.ui.theme.designVerticalPxToDp
import com.gios.lightcamera.ui.theme.gridUnitsAsDp
import com.gios.lightcamera.ui.theme.lightClickable
import com.gios.lightcamera.ui.theme.lightTextStyle

/**
 * **Who**, not which app.
 *
 * The system share sheet asks which application should receive a photograph, which on a phone
 * with three applications is a question with an obvious answer wrapped in a grid of icons —
 * and it is a colour Material bottom sheet on a monochrome panel, so it also looks like
 * somebody else's software. The question actually being asked is who the photograph is for.
 *
 * Android will not let a third-party app ask that: the row of faces at the top of the stock
 * chooser is built from *sharing shortcuts*, which an app publishes for the system's own UI,
 * and there is no API to read another app's. So this owns the address book itself and hands
 * the result to a messaging app already addressed. See [Handoff].
 *
 * A full screen, not a sheet — LightOS has no bottom sheets, and something that half-covers a
 * photograph is a Material idiom rather than a Light one.
 */
@Composable
fun SendSheet(
    photos: List<Photo>,
    recentKeys: List<String>,
    onRemember: (String) -> Unit,
    onNotice: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colours = LightThemeTokens.colors

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    /**
     * **Refused twice means the dialog is gone for good**, and the button that asks for it
     * becomes permanently inert with nothing to explain why. Android reports that state only
     * indirectly: after a denial, `shouldShowRequestPermissionRationale` goes *false* — the
     * system is saying it will no longer ask. So the button changes to one that opens the app's
     * own settings page, which is the only route left.
     */
    val activity = context as? android.app.Activity
    var blocked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok && activity != null) {
            blocked = !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
        }
    }
    // Granting from the settings page happens outside this app, so the answer has to be re-read
    // on the way back rather than waiting for another tap.
    LifecycleResumeEffect(Unit) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) blocked = false
        onPauseOrDispose { }
    }

    var all by remember { mutableStateOf<List<Recipient>?>(null) }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        all = ContactsRepo(context).load()
    }

    var query by remember { mutableStateOf("") }

    // Back closes the picker rather than the photograph behind it: this is a step in a task,
    // and backing out of it should land where you were.
    BackHandler(enabled = true, onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colours.background)
            // The picker is drawn over the viewer, and Compose does not consume touches for a
            // background — without this, taps fall through to the photograph underneath.
            .swallowTaps(),
    ) {
        // ---- header ----------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(3f.gridUnitsAsDp())
                .padding(horizontal = 1f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeIcon(icon = LightIcons.Back, onClick = onClose)
            Spacer(Modifier.weight(1f))
            LightText(
                text = if (photos.size == 1) "SEND TO" else "SEND ${photos.size} TO",
                variant = LightTextVariant.Detail,
            )
            Spacer(Modifier.weight(1f))
            // Balances the back chevron so the title sits on the centre line rather than
            // being pushed off it by an icon on one side only.
            Spacer(Modifier.width(2f.gridUnitsAsDp()))
        }

        when {
            !granted -> Column(
                modifier = Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LightText("Who is it for?", LightTextVariant.Subheading, align = TextAlign.Center)
                LightText(
                    "Roll shows your own contacts here instead of a grid of apps, so it needs to read them. " +
                        "They are read on this phone and nothing is sent anywhere.",
                    LightTextVariant.Paragraph,
                    lighten = true,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (blocked) {
                    LightText(
                        "Contacts are blocked for Roll. Turn them on in the phone's app settings.",
                        LightTextVariant.Detail,
                        lighten = true,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    LightText(
                        "OPEN SETTINGS",
                        LightTextVariant.Button,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .lightClickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.parse("package:" + context.packageName)),
                                    )
                                }
                            },
                    )
                } else {
                    LightText(
                        "ALLOW",
                        LightTextVariant.Button,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .lightClickable { ask.launch(Manifest.permission.READ_CONTACTS) },
                    )
                }
            }

            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LightText("Reading contacts…", LightTextVariant.Paragraph, lighten = true)
            }

            else -> {
                val loaded = all.orEmpty()
                val filtered = remember(loaded, query) {
                    if (query.isBlank()) loaded else loaded.filter { Recipients.matches(it, query) }
                }
                // Recents only in the resting state. Once something has been typed the user has
                // said who they are looking for, and a "recent" heading above the answer is a
                // second list to read past.
                val ordered = remember(filtered, recentKeys, query) {
                    if (query.isBlank()) {
                        Recipients.ordered(filtered, recentKeys)
                    } else {
                        Recipients.Ordered(emptyList(), filtered)
                    }
                }

                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )

                val send: (Recipient) -> Unit = { who ->
                    // `forPhoto`, not `primary`: a contact with a starred email address and a
                    // mobile should get the photograph at the mobile.
                    val address = who.forPhoto
                    if (address == null) {
                        onNotice("No way to reach ${who.name}")
                    } else {
                        when (val outcome = Handoff.send(context, photos.map { it.uri }, address)) {
                            is Handoff.Outcome.Sent -> {
                                onRemember(address.key)
                                onClose()
                            }
                            Handoff.Outcome.Chooser -> {
                                onNotice("Nothing here can address a photo — pick an app")
                                onClose()
                            }
                            is Handoff.Outcome.Failed -> onNotice(outcome.why)
                        }
                    }
                }

                if (loaded.isEmpty()) {
                    EmptyState(
                        text = "No contacts on this phone.",
                        detail = "Add somebody to the address book and they will appear here.",
                    )
                } else if (filtered.isEmpty()) {
                    EmptyState(text = "Nobody matches “${query.trim()}”.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (ordered.recent.isNotEmpty()) {
                            item(key = "recent-heading") { SectionHeading("RECENT") }
                            items(ordered.recent, key = { "recent-${it.id}" }) { who ->
                                RecipientRow(who, onClick = { send(who) })
                            }
                            item(key = "recent-rule") {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = colours.rule,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                        }
                        items(ordered.rest, key = { it.id }) { who ->
                            RecipientRow(who, onClick = { send(who) })
                        }
                        // The last row clears the gesture strip.
                        item(key = "tail") { Spacer(Modifier.height(4f.gridUnitsAsDp())) }
                    }
                }
            }
        }
    }
}

/**
 * The SDK's text field: a 3-design-pixel rule under the text, no container and no floating
 * label. Material's filled box appears nowhere in LightOS.
 */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colours = LightThemeTokens.colors
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Box {
            if (value.isEmpty()) {
                LightText("Search", LightTextVariant.Copy, lighten = true)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = lightTextStyle(LightTextVariant.Copy).copy(color = colours.content),
                cursorBrush = SolidColor(colours.content),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalDivider(
            // 3 design pixels, the SDK's underline weight.
            thickness = 3f.designVerticalPxToDp(),
            color = colours.contentSecondary,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.8f),
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Superfine,
        lighten = true,
        modifier = Modifier.padding(start = 1f.gridUnitsAsDp(), top = 8.dp, bottom = 2.dp),
    )
}

/**
 * One person. Name over address, which is the SDK's list row — `copy` over `detail`.
 *
 * No avatar. Contact photos are a colour circle on a greyscale panel, and reading them means a
 * bitmap per row out of the contacts provider; a name is what you are reading anyway.
 */
@Composable
private fun RecipientRow(who: Recipient, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 8.dp),
    ) {
        LightText(who.name, LightTextVariant.Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val subtitle = who.subtitle
        if (subtitle.isNotBlank()) {
            LightText(
                subtitle,
                LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
