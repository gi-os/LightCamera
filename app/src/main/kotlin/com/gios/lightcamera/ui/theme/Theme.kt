package com.gios.lightcamera.ui.theme

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Light Phone III design language, ported from `lightphone/light-sdk` (MIT licence,
 * © 2026 The Light Phone — see LICENSE-light-sdk) so that a plain sideloaded APK looks
 * and behaves like a tool built against the SDK.
 *
 *  - **A 27 x 31 grid.** Sizes and gaps are fractions of the screen, not fixed dp.
 *  - **A named type scale, scaled by screen height** against a 600px baseline.
 *  - **Three colours.** The panel is greyscale, so state is carried by inversion,
 *    brackets and weight rather than by hue.
 *
 * A camera adds one thing the SDK has no need for: chrome that has to stay legible on top
 * of a live image. Hence [Scrim] and [OnImage] below, which are this app's own.
 */

/* ---------------- grid ---------------- */

object LightGrid {
    const val WIDTH = 27
    const val HEIGHT = 31
}

@Composable
fun Float.gridUnitsAsDp(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return (screenWidthDp.toFloat() / LightGrid.WIDTH * this).dp
}

@Composable
fun Float.verticalGridUnitsAsDp(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return (screenHeightDp.toFloat() / LightGrid.HEIGHT * this).dp
}

/**
 * The height the type scale's design pixels were drawn against.
 *
 * **This one number is why every label in the app was too small**, and no amount of moving
 * individual readouts up the scale was going to fix it. The SDK divides each design pixel by 600
 * — but the LPIII's panel is about 477dp tall, so every size came out at 0.79 of what the scale
 * intends: `Detail` at 20 design px rendered as 16sp, `Superfine` at 16 as 12.7sp. The scale was
 * being asked to describe a 600dp screen and then applied to a much shorter one.
 *
 * Set to the panel's own height, so a design pixel is a point and the scale means what it says.
 * Everything gets about a quarter larger at once, which is the correct place to make that change:
 * one number, every screen, and the proportions between the variants are untouched.
 */
private const val FONT_VERTICAL_SCALE_BASELINE_PX = 477f

@Composable
fun Float.designVerticalPxToSp(): TextUnit {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    return (this * screenHeightDp / FONT_VERTICAL_SCALE_BASELINE_PX).sp
}

@Composable
fun Float.designVerticalPxToDp(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    return (this * screenHeightDp / FONT_VERTICAL_SCALE_BASELINE_PX).dp
}

/* ---------------- colours ---------------- */

@Immutable
data class LightColors(
    val background: Color,
    val content: Color,
    val contentSecondary: Color,
    val contentFaint: Color,
    val rule: Color,
    /** Behind chrome that sits on the live image. */
    val scrim: Color,
    /** Masked-off area outside the frame lines. */
    val matte: Color,
)

object LightThemeColors {
    val Dark = LightColors(
        background = Color.Black,
        content = Color.White,
        contentSecondary = Color(0xFFBBBBBB),
        contentFaint = Color(0xFF5E5E5E),
        rule = Color(0xFF262626),
        scrim = Color(0x99000000),
        matte = Color(0xCC000000),
    )
}

/* ---------------- typography ---------------- */

@Immutable
data class LightTypography(
    val title: TextStyle,
    val subtitle: TextStyle,
    val heading: TextStyle,
    val subheading: TextStyle,
    val copy: TextStyle,
    val button: TextStyle,
    val paragraph: TextStyle,
    val paragraphWide: TextStyle,
    val detail: TextStyle,
    val fine: TextStyle,
    val superfine: TextStyle,
    val micro: TextStyle,
)

/** Mirrors the LP3 table in LightOS's own `style/index.ts`, unscaled. */
private fun buildTypography(fontFamily: FontFamily): LightTypography = LightTypography(
    title = TextStyle(
        fontSize = 115.sp, fontFamily = fontFamily, fontWeight = FontWeight.Light,
        lineHeight = (115 * 1.10).sp,
    ),
    subtitle = TextStyle(
        fontSize = 52.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        lineHeight = (52 * 1.20).sp,
    ),
    heading = TextStyle(
        fontSize = 38.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        lineHeight = (38 * 1.35).sp,
    ),
    subheading = TextStyle(
        fontSize = 30.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        letterSpacing = (30 * 0.03).sp, lineHeight = (30 * 1.25).sp,
    ),
    copy = TextStyle(
        fontSize = 30.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        lineHeight = (30 * 1.50).sp,
    ),
    button = TextStyle(
        fontSize = 30.sp, fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        letterSpacing = (30 * 0.15).sp, lineHeight = (30 * 1.10).sp,
    ),
    paragraph = TextStyle(
        fontSize = 24.5.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        lineHeight = (24.5 * 1.25).sp,
    ),
    paragraphWide = TextStyle(
        fontSize = 25.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        letterSpacing = (25 * 0.02).sp, lineHeight = (25 * 1.30).sp,
    ),
    detail = TextStyle(
        fontSize = 20.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        lineHeight = (20 * 1.45).sp,
    ),
    fine = TextStyle(
        fontSize = 25.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        letterSpacing = (25 * 0.03).sp, lineHeight = (25 * 1.15).sp,
    ),
    superfine = TextStyle(
        fontSize = 16.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        lineHeight = (16 * 1.20).sp,
    ),
    micro = TextStyle(
        fontSize = 8.sp, fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        lineHeight = (8 * 1.20).sp,
    ),
)

private val FallbackTypography = buildTypography(FontFamily.Default)

@Composable
private fun rememberLightTypography(): LightTypography {
    val fam = remember { akkuratFamilyOrDefault() }
    return remember(fam) { buildTypography(fam) }
}

val LocalLightColors = staticCompositionLocalOf { LightThemeColors.Dark }
val LocalLightTypography = staticCompositionLocalOf { FallbackTypography }

object LightThemeTokens {
    val colors: LightColors
        @Composable get() = LocalLightColors.current

    val typography: LightTypography
        @Composable get() = LocalLightTypography.current
}

/* ---------------- theme ---------------- */

private fun LightColors.toMaterialScheme(): ColorScheme = darkColorScheme(
    background = background,
    surface = background,
    onBackground = content,
    onSurface = content,
    primary = content,
    onPrimary = background,
    secondary = contentSecondary,
    onSecondary = background,
    surfaceVariant = background,
    onSurfaceVariant = contentSecondary,
    outline = rule,
)

@Composable
fun LightCameraTheme(content: @Composable () -> Unit) {
    val colors = LightThemeColors.Dark
    CompositionLocalProvider(
        LocalLightColors provides colors,
        LocalLightTypography provides rememberLightTypography(),
    ) {
        MaterialTheme(colorScheme = colors.toMaterialScheme(), content = content)
    }
}

/* ---------------- touch ---------------- */

object LightHaptics {
    /** Tuned for the LP3's slow motor, same as the SDK. */
    fun click(context: Context) {
        buzz(context, 45L)
    }

    /** The shutter. Longer and harder, so it lands like a mechanical release. */
    fun shutter(context: Context) {
        buzz(context, 22L)
    }

    /** One frame of film advancing. */
    fun advance(context: Context) {
        buzz(context, 12L)
    }

    private fun buzz(context: Context, ms: Long) {
        runCatching {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                ?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

/**
 * Clickable with no ripple and no press state, buzzing on finger-down the way LightOS
 * does. A ripple would be the single most un-Light thing in the app.
 */
fun Modifier.lightClickable(
    enabled: Boolean = true,
    haptics: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val buzz = enabled && haptics
    pointerInput(buzz) {
        if (!buzz) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            LightHaptics.click(context)
        }
    }.clickable(
        interactionSource = null,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * [lightClickable] with a long press, for the roll's cells.
 *
 * Kept separate rather than folded into the one modifier because `combinedClickable` changes
 * the *tap* as well: with a long-press handler attached, every single tap has to wait out the
 * long-press timeout before it fires. That is the right trade on a grid cell where holding
 * means "select this", and the wrong one on a text button, so it is opted into.
 *
 * The 45ms buzz still lands on finger-down, so a press that turns out to be a long one has
 * already acknowledged itself.
 *
 * `@OptIn` because the overload taking `interactionSource` and `indication` is still marked
 * experimental for `combinedClickable`, while the identical one on `clickable` is stable. It is
 * the overload that lets the indication be *nothing* — without it, `LocalIndication` supplies
 * Material's ripple, and LightOS has no ripples anywhere.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.lightCombinedClickable(
    enabled: Boolean = true,
    haptics: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val buzz = enabled && haptics
    pointerInput(buzz) {
        if (!buzz) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            LightHaptics.click(context)
        }
    }.combinedClickable(
        interactionSource = null,
        indication = null,
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
