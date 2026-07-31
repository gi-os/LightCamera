package com.gios.lightcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gios.lightcamera.ui.theme.LightIcon
import com.gios.lightcamera.ui.theme.LightIconSpec
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import com.gios.lightcamera.ui.theme.gridUnitsAsDp
import com.gios.lightcamera.ui.theme.lightClickable
import com.gios.lightcamera.ui.theme.lightTextStyle
import com.gios.lightcamera.ui.theme.verticalGridUnitsAsDp

/**
 * The handful of list-and-form pieces the reporting screens need, and nothing else.
 *
 * Roll's own vocabulary is a camera's — a viewfinder, chrome that rotates with the phone, an
 * album. It has no rows, no fields and no buttons, because until now it never had a form. Rather
 * than push form widgets into Roll's design files for the sake of one sheet, the reporting UI
 * brings its own, kept deliberately identical to the ones in gi-os/LightNotebook.
 *
 * That sameness is the point. This is diagnostic UI, not product surface: it should look the same
 * in every app that has it, so that filing a report is one learned gesture rather than four, and
 * so the whole thing ports by copying files rather than by redesigning.
 */
/** The margin every full-width row and field sits inside. */
@Composable
fun lightInset(): Dp = 1.4f.gridUnitsAsDp()

/** A hairline. Greyscale has no tints, so a rule is how a section ends. */
@Composable
fun LightRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LightThemeTokens.colors.rule),
    )
}

/** Selection inverts rather than tints — the only state change that survives greyscale. */
@Composable
fun LightChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LightThemeTokens.colors
    Box(
        modifier
            .height(2.2f.verticalGridUnitsAsDp())
            .background(if (selected) colors.content else colors.background)
            .border(1.dp, if (selected) colors.content else colors.rule)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.8f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Detail,
            color = if (selected) colors.background else colors.content,
            maxLines = 1,
        )
    }
}

/** A full-width action. Inverted, because it is the one thing to do on the screen. */
@Composable
fun LightWideButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(3.4f.verticalGridUnitsAsDp())
            .background(if (filled && enabled) colors.content else colors.background)
            .border(1.dp, if (enabled) colors.content else colors.rule)
            .lightClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Button,
            color = when {
                !enabled -> colors.rule
                filled -> colors.background
                else -> colors.content
            },
            maxLines = 1,
        )
    }
}

/**
 * A single line of editable text, underlined the way the SDK underlines its fields. Material's
 * own text fields bring a filled container and a floating label, neither of which exists in
 * LightOS.
 */
@Composable
fun LightInlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    variant: LightTextVariant = LightTextVariant.Copy,
) {
    val colors = LightThemeTokens.colors
    Column(modifier) {
        Box {
            if (value.isEmpty()) {
                LightText(placeholder, variant, lighten = true, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = lightTextStyle(variant).copy(color = colors.content),
                cursorBrush = SolidColor(colors.content),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .padding(top = 0.4f.verticalGridUnitsAsDp())
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.content),
        )
    }
}

/** A row in a list. Everything is a full-width row here; the eye only ever scans one column. */
@Composable
fun LightListRow(
    title: String,
    sub: String? = null,
    trailing: LightIconSpec? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.lightClickable(onClick = onClick) else it }
            .heightIn(min = 3.2f.verticalGridUnitsAsDp())
            .padding(horizontal = lightInset(), vertical = 0.6f.verticalGridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            LightText(title, LightTextVariant.Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) {
                LightText(
                    text = sub,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            LightIcon(trailing, size = 1.2f.gridUnitsAsDp(), modifier = Modifier.padding(start = 0.6f.gridUnitsAsDp()))
        }
    }
}
