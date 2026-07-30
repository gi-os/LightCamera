package com.gios.lightcamera.hw

import android.app.Activity
import android.view.KeyEvent

/**
 * Every physical control on the phone, pointed at the camera.
 *
 * Lives in the activity because [Activity.dispatchKeyEvent] is the one place that sees a
 * key before the view hierarchy does, which is what makes it beat a focused view.
 *
 * The mapping, which is the whole argument for building this app rather than using the
 * stock one:
 *
 *  - **Camera button, half press** → autofocus, on the tracked face if there is one.
 *  - **Camera button, pressed through** → shutter. See [ShutterRelease].
 *  - **Turn the wheel** → zoom. The wheel is a lens ring.
 *  - **Hold the wheel in and turn** → exposure compensation, in thirds of a stop.
 *  - **Click the wheel** → torch, the phone's own behaviour for that press.
 *
 * The press-and-turn split works because a held `WHEEL_CLICK` produces no key repeat: DOWN
 * arrives, notches arrive, UP arrives. So the press is a modifier, and whether it was
 * *only* a press is known by the time UP lands.
 */
class LightControls(
    private val activity: Activity,
    private val wheel: WheelBus,
    private val shutter: ShutterRelease,
    private val onTorchToggle: () -> Unit,
) {

    private var clickHeld = false

    /** Whether this press has already been spent as a modifier. */
    private var clickSpent = false

    /** True if [event] was one of ours and has been dealt with. */
    fun dispatch(event: KeyEvent): Boolean {
        val key = LightKeys.of(event) ?: return false
        val down = event.action == KeyEvent.ACTION_DOWN

        when (key) {
            LightKey.WheelClick -> {
                if (down) {
                    if (event.repeatCount == 0) {
                        clickHeld = true
                        clickSpent = false
                    }
                } else {
                    clickHeld = false
                    // A press that moved the wheel was an exposure gesture; firing the
                    // torch on the way out of it would blow the next frame.
                    if (!clickSpent) onTorchToggle()
                }
            }

            LightKey.WheelUp, LightKey.WheelDown -> {
                // One notch is a complete DOWN+UP pair, so act on DOWN and swallow the UP.
                if (!down) return true
                val notches = if (key == LightKey.WheelUp) 1 else -1
                if (clickHeld) clickSpent = true
                wheel.send(notches, pressed = clickHeld)
            }

            LightKey.Focus, LightKey.Camera -> {
                // Repeats can't happen on these keys, but guard anyway: a synthetic repeat
                // from a future LightOS would otherwise machine-gun the shutter.
                if (down && event.repeatCount > 0) return true
                shutter.onKey(key, down)
            }
        }
        return true
    }

    /** So a settings screen can tell the user whether their build maps the wheel at all. */
    fun wheelSupported(): Boolean = LightKeys.wheelLabelsPresent()
}
