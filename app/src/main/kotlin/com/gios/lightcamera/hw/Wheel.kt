package com.gios.lightcamera.hw

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Wheel notches on their way from the activity to whatever is on screen.
 *
 * One notch per event, positive for up. The activity is the only thing that can see the
 * key, but only the current screen knows what turning the wheel means, so the two are
 * joined by a flow rather than by the activity reaching into the UI.
 *
 * Two flows, not one: a bare turn and a turn with the wheel held in are different
 * gestures, and every screen wants them for different things. On the viewfinder that is
 * zoom versus exposure compensation; in the roll it is scrolling versus nothing at all.
 *
 * [SharedFlow] with no replay, deliberately: a notch that arrives while nothing is
 * listening is gone, which is what you want from a physical control. Buffered generously
 * because the sensor emits bursts far faster than a frame.
 */
class WheelBus {
    private val _turns = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    private val _pressedTurns = MutableSharedFlow<Int>(extraBufferCapacity = 64)

    /** Bare turns. */
    val turns: SharedFlow<Int> = _turns.asSharedFlow()

    /** Turns made with the wheel held in. */
    val pressedTurns: SharedFlow<Int> = _pressedTurns.asSharedFlow()

    fun send(notches: Int, pressed: Boolean) {
        if (pressed) _pressedTurns.tryEmit(notches) else _turns.tryEmit(notches)
    }
}

val LocalWheelBus = staticCompositionLocalOf<WheelBus?> { null }

/**
 * Distance per notch when the wheel is scrolling a list. About six notches to a screenful
 * on the LPIII panel.
 */
private val NOTCH = 64.dp

/**
 * Which way a notch moves the page. `1` means turning the wheel up moves you *down* the
 * list — the wheel drags the content the way a finger flick does.
 */
private const val DIRECTION = 1

/**
 * Fraction of the remaining distance applied per frame.
 *
 * The sensor fires a notch every ~35 ms, faster than a frame, so applying each one on
 * arrival produces a stack of instant jumps. Instead every notch adds to a debt and each
 * frame pays off a share of it, so one notch glides and a fast spin becomes a single sweep
 * that keeps moving slightly after your thumb stops. 0.28 settles ~90% inside seven frames.
 */
private const val SMOOTHING = 0.28f

/**
 * Notches needed to start, and how long a turn stays live.
 *
 * The wheel sits under a thumb and catches stray brushes. The first notch after a pause
 * buys nothing on its own: it is remembered, and a second releases both. Once turning,
 * everything applies immediately until [IDLE_MS] passes with the wheel still.
 */
private const val ARM_NOTCHES = 2
private const val IDLE_MS = 1_500L

/**
 * Point the wheel at a Compose scroller. Works for `ScrollState` and lazy states alike.
 *
 * Pass `reverse = true` for a `reverseLayout` list. Reversing the layout reverses the scroll
 * axis with it, so `scrollBy` with a positive delta walks *away* from the pinned end — in the
 * roll, up towards last year rather than down towards the viewfinder. The wheel has to mean
 * the same thing on every screen, so the sign is flipped here instead of the list being left
 * feeling backwards.
 */
@Composable
fun WheelScroll(state: ScrollableState, active: Boolean = true, reverse: Boolean = false) {
    val step = with(LocalDensity.current) { NOTCH.toPx() }
    val debt = remember { Debt() }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val direction = if (reverse) -DIRECTION else DIRECTION

    WheelTurns(active = active, armed = true) { notches ->
        debt.px += notches * step * direction
        wake.trySend(Unit)
    }

    LaunchedEffect(state, wake) {
        while (true) {
            // Suspends while the wheel is still, so an idle screen costs nothing.
            wake.receive()
            state.scroll {
                while (abs(debt.px) > 0.5f) {
                    withFrameNanos { }
                    val wanted = (debt.px * SMOOTHING).let {
                        // Never stall a notch out in sub-pixel increments.
                        if (abs(it) < 1f) debt.px else it
                    }
                    debt.px -= wanted
                    val consumed = scrollBy(wanted)
                    // At an edge the rest of the debt is unpayable, and keeping it would
                    // mean the next turn back spends its first notches on nothing.
                    if (abs(consumed) < abs(wanted) - 0.5f) debt.px = 0f
                }
            }
        }
    }
}

/**
 * Raw notches, optionally behind the stray-brush guard.
 *
 * [armed] false is for controls where every notch has to count and a stray one is
 * harmless — stepping through filters, say, where the worst case is one wrong name on
 * screen. Zoom and exposure use the guard, because a stray notch there changes the photo.
 *
 * Armed state lives in the effect rather than in composition state: it is a property of
 * the turn in progress, and a recomposition mid-turn should not disarm the wheel.
 */
@Composable
fun WheelTurns(
    active: Boolean = true,
    armed: Boolean = false,
    pressed: Boolean = false,
    onNotch: (Int) -> Unit,
) {
    val handler by rememberUpdatedState(onNotch)
    val bus = LocalWheelBus.current ?: return
    LaunchedEffect(bus, active, armed, pressed) {
        if (!active) return@LaunchedEffect
        val flow = if (pressed) bus.pressedTurns else bus.turns
        if (!armed) {
            flow.collect { handler(it) }
            return@LaunchedEffect
        }
        var isArmed = false
        var held = 0
        var count = 0
        var last = 0L
        flow.collect { notches ->
            val now = System.nanoTime() / 1_000_000
            if (now - last > IDLE_MS) {
                isArmed = false
                held = 0
                count = 0
            }
            last = now
            if (isArmed) {
                handler(notches)
                return@collect
            }
            held += notches
            count++
            if (count >= ARM_NOTCHES) {
                isArmed = true
                // Release what the guard was holding, so nothing deliberate is lost.
                if (held != 0) handler(held) else handler(notches.sign)
                held = 0
            }
        }
    }
}

/**
 * Distance still owed to the scroller. Deliberately not Compose state: nothing in
 * composition reads it, and making it observable would restart the glide on every
 * recomposition it caused.
 */
private class Debt {
    @Volatile
    var px: Float = 0f
}
