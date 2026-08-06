package com.gios.lightcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gios.lightcamera.ocr.Found
import com.gios.lightcamera.ocr.TextScan
import com.gios.lightcamera.qr.Codes
import com.gios.lightcamera.ui.theme.LightIcons
import com.gios.lightcamera.ui.theme.lightClickable
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens

/**
 * What a photograph said, over the photograph.
 *
 * Laid out as the same sheet a scanned QR code gets, on purpose and not for tidiness: after
 * [TextScan] has run, a phone number photographed off a business card and a phone number inside a
 * QR code are the same value with the same actions, and they should not arrive in two different
 * screens. Heading, the thing you care about, the evidence underneath, then the verbs.
 *
 * The one structural difference is that a page yields *several* things and a code yields one. So
 * the top of the sheet is a short list, tapping a row selects it, and the actions act on the
 * selection — rather than a wall of buttons, which on a 3.92" panel is unreadable at exactly the
 * moment you are holding the phone over something.
 *
 * A page with no addresses, numbers or links in it skips the list entirely and is just text with
 * a COPY on it, which is the receipt case and the most common one.
 */
@Composable
fun TextSheet(
    text: String,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = LightThemeTokens.colors
    val found = remember(text) { TextScan.found(text) }
    val page = remember(text) { TextScan.page(text) }
    var selected by remember(text) { mutableStateOf<Found?>(found.firstOrNull()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colours.background)
            // Eats every touch, or a tap meant for a row would reach the pager underneath and
            // change the photograph out from under the reading.
            .swallowTaps(),
    ) {
        // `opaque = false`: the Box above already painted the panel, and a second fill inside the
        // rotation letterboxes the corners in a different black.
        RotatedToDevice(quarter = rememberDeviceQuarter(), opaque = false) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LightText(TextScan.heading(found), LightTextVariant.Detail)
                        Spacer(Modifier.weight(1f))
                        ChromeIcon(icon = LightIcons.Close, lighten = true, onClick = onClose)
                    }
                    Spacer(Modifier.height(6.dp))

                    val current = selected
                    if (current != null) {
                        // The reading, not the interpretation. `Codes.title` would show the host;
                        // what was photographed is what should be checked against the page in your
                        // other hand, because the two differ exactly when the recogniser slipped.
                        LightText(current.label, LightTextVariant.Heading)
                    } else {
                        LightText(firstLine(page), LightTextVariant.Heading)
                    }
                    Spacer(Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        found.forEach { item ->
                            TextFoundRow(
                                item = item,
                                selected = item.payload == current?.payload,
                                onTap = { selected = item },
                            )
                        }
                        if (found.isNotEmpty()) Spacer(Modifier.height(10.dp))
                        LightText(page, LightTextVariant.Detail, lighten = true)
                    }

                    Spacer(Modifier.height(4.dp))
                    if (current != null) {
                        // Only when there is somewhere to go. A row you cannot press is a row
                        // explaining itself — the same rule the QR sheet follows.
                        Codes.openable(current.payload)?.let { target ->
                            TextAction(label = openLabel(current.kind)) { onOpen(target) }
                        }
                        TextAction(label = "Copy ${shortKind(current.kind)}", lighten = true) {
                            onCopy(current.label)
                        }
                    }
                    TextAction(label = "Copy all text", lighten = current != null) { onCopy(page) }
                    TextAction(label = "Done", lighten = true, onTap = onClose)
                }
            }
        }
    }
}

/**
 * One found thing.
 *
 * Selection is shown by the same filled/outline pair the roll uses for starring, because on this
 * panel a highlight colour does not exist and a bracket around a row of variable length reads
 * worse than a mark in a fixed column.
 */
@Composable
private fun TextFoundRow(item: Found, selected: Boolean, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable { onTap() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIcon(
            icon = if (selected) LightIcons.SelectOn else LightIcons.SelectOff,
            lighten = !selected,
            size = 18.dp,
            onClick = onTap,
        )
        Spacer(Modifier.height(0.dp))
        Column(Modifier.padding(start = 10.dp)) {
            LightText(item.label, LightTextVariant.Detail, lighten = !selected)
        }
    }
}

@Composable
private fun TextAction(label: String, lighten: Boolean = false, onTap: () -> Unit) {
    LightText(
        text = label.uppercase(),
        variant = LightTextVariant.Button,
        lighten = lighten,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable { onTap() }
            .padding(vertical = 12.dp),
    )
}

/** What pressing OPEN will actually do, said before it happens. */
private fun openLabel(kind: Codes.Kind): String = when (kind) {
    Codes.Kind.Phone -> "Call"
    Codes.Kind.Email -> "Write"
    Codes.Kind.Sms -> "Message"
    Codes.Kind.Place -> "Map"
    else -> "Open"
}

private fun shortKind(kind: Codes.Kind): String = when (kind) {
    Codes.Kind.Phone -> "number"
    Codes.Kind.Email -> "address"
    Codes.Kind.Link -> "link"
    else -> "this"
}

private fun firstLine(page: String): String =
    page.lineSequence().firstOrNull { it.isNotBlank() }?.take(60).orEmpty().ifBlank { "No text" }
