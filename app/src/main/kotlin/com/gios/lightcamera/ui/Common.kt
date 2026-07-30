package com.gios.lightcamera.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.lightcamera.ui.theme.LightIcon
import com.gios.lightcamera.ui.theme.LightIconSpec
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import com.gios.lightcamera.ui.theme.lightClickable
import kotlin.math.abs
import kotlin.math.atan2

/** A word or two over the image. Reads as an annotation, not as a toast. */
@Composable
fun Notice(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    val colors = LightThemeTokens.colors
    Box(
        modifier = modifier
            .background(colors.scrim, RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        LightText(text.uppercase(), LightTextVariant.Superfine)
    }
}

/** An icon that behaves: no ripple, a buzz on finger-down, a generous invisible target. */
@Composable
fun ChromeIcon(
    icon: LightIconSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lighten: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val colors = LightThemeTokens.colors
    Box(
        modifier = modifier
            .lightClickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        LightIcon(
            icon = icon,
            size = size,
            tint = if (lighten) colors.contentSecondary else colors.content,
        )
    }
}

/** A word used as a button, tracked out the way LightOS sets its bar labels. */
@Composable
fun ChromeLabel(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lighten: Boolean = false,
) {
    LightText(
        text = text.uppercase(),
        variant = LightTextVariant.Superfine,
        lighten = lighten,
        modifier = modifier
            .lightClickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
fun EmptyState(text: String, detail: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightText(text, LightTextVariant.Subheading, align = TextAlign.Center)
        if (detail != null) {
            LightText(
                detail,
                LightTextVariant.Paragraph,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * How far the phone is rolled from upright, in degrees, for the horizon line.
 *
 * The accelerometer rather than the rotation vector: gravity is all this needs, the
 * accelerometer is the cheapest sensor on the phone, and the rotation vector would drag in
 * the magnetometer and with it every fridge magnet in the room.
 *
 * Smoothed hard — a raw accelerometer feed makes a horizon line that shivers, and a
 * shivering level is worse than none. The coefficient is a compromise: high enough that
 * deliberate tilting tracks your hand, low enough that a heartbeat doesn't show.
 */
@Composable
fun rememberTilt(active: Boolean = true): State<Float> {
    val context = LocalContext.current
    val degrees = remember { mutableFloatStateOf(0f) }
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                // Portrait upright is x≈0, y≈+9.8. atan2 of the two is the roll, and it
                // stays sane past 90 degrees where asin would fold back on itself.
                val raw = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                if (abs(raw) > 120f) return
                degrees.floatValue += (raw - degrees.floatValue) * SMOOTHING
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return degrees
}

private const val SMOOTHING = 0.12f

/** Whether the phone is level enough to say so. A degree either way is within a hand's steadiness. */
fun Float.isLevel(): Boolean = abs(this) < 1.0f
