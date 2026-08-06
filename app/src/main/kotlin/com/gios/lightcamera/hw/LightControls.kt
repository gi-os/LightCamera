package com.gios.lightcamera.hw

import android.app.Activity
import android.view.KeyEvent
import com.gios.light.common.hw.LightKey
import com.gios.light.common.hw.LightKeys
import com.gios.light.common.hw.WheelBus

/**
 * Every physical control on the phone, pointed at the camera.
 *
 * Lives in the activity because [Activity.dispatchKeyEvent] is the one place that sees a
 * key before the view hierarchy does, which is what makes it beat a focused view.
 *
 * The camera button is not remappable and never will be:
 *
 *  - **Camera button, half press** → autofocus, on the tracked face if there is one.
 *  - **Camera button, pressed through** → shutter. See [ShutterRelease].
 *
 * Everything else is a [Binding] the user can point somewhere, defaulting to the mapping the
 * app shipped with — wheel turns the filters, press-and-turn is exposure, click is the torch,
 * either volume key is a shutter. See [Controls].
 *
 * The press-and-turn split works because a held `WHEEL_CLICK` produces no key repeat: DOWN
 * arrives, notches arrive, UP arrives. So the press is a modifier, and whether it was
 * *only* a press is known by the time UP lands.
 */
class LightControls(
    private val activity: Activity,
    private val wheel: WheelBus,
    private val shutter: ShutterRelease,
    /**
     * Read at the moment of the press, not captured once. Changing a binding in settings has to
     * take effect on the next press without rebuilding this object, and settings is a screen
     * inside the same activity that owns it.
     */
    private val pressFor: (Binding) -> PressAction,
    private val onPress: (PressAction) -> Unit,
    private val dialFor: (Binding) -> DialAction,
    private val onDial: (DialAction, Int) -> Unit,
) {

    private var clickHeld = false

    /** Whether this press has already been spent as a modifier. */
    private var clickSpent = false

    /** True if [event] was one of ours and has been dealt with. */
    fun dispatch(event: KeyEvent): Boolean {
        if (volumeKey(event)) return true
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
                    // **A press that moved the wheel was a press-and-turn, not a click.** Spent
                    // whether or not press-and-turn is pointed at anything: turning the wheel while
                    // holding it in is physically not a click, and firing the click's action on the
                    // way out of that gesture — the torch, by default, straight into the next frame
                    // — is the bug this flag was added for.
                    if (!clickSpent) onPress(pressFor(Binding.WheelClick))
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

    /**
     * The volume keys, pointed wherever they have been pointed.
     *
     * Both default to the shutter, a convention borrowed from every other camera, and here it
     * earns its keep twice over: there is no shutter button on the screen, so if the camera key
     * is being swallowed by something — an accessibility service that binds it, most likely
     * LightControl — this is the difference between a camera and an ornament. Volume keys are
     * AOSP keycodes, so no label resolution is needed and this works on any build.
     *
     * **`Nothing` gives the key back rather than eating it.** Returning false here lets the event
     * carry on to the system, so a volume key that has been deliberately unbound adjusts the
     * volume like it does everywhere else on the phone. Swallowing it silently would be the worse
     * of the two readings of "nothing".
     */
    private fun volumeKey(event: KeyEvent): Boolean {
        val up = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return false
        }

        // **A dial first, because a dial is what two keys either side of a phone are.** One means
        // more and the other means less; that is the only reason there are two of them, and the
        // old mapping — both pointed at the same press — threw it away.
        val dial = dialFor(Binding.VolumeKeys)
        if (dial != DialAction.Nothing) {
            // Repeats are allowed here and nowhere else in this file. Holding the rocker to run
            // through the filters is the gesture, and it is the one thing the wheel can do that a
            // pair of keys otherwise cannot. Android's own repeat rate does the pacing.
            if (event.action == KeyEvent.ACTION_DOWN) onDial(dial, if (up) 1 else -1)
            return true
        }

        val action = pressFor(Binding.VolumeKeys)
        if (action == PressAction.Nothing) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onPress(action)
        return true
    }

    /** So a settings screen can tell the user whether their build maps the wheel at all. */
    fun wheelSupported(): Boolean = LightKeys.wheelLabelsPresent()
}
