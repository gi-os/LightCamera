package com.gios.lightcamera.hw

/**
 * What each physical control on the phone is pointed at.
 *
 * The app shipped with one mapping, chosen because it is the right one: the wheel walks the
 * filters, holding it in and turning is exposure, clicking it is the torch, and either volume
 * key is a shutter. That is still the default and nothing here changes it.
 *
 * It is a *preference* now because the LPIII's controls are shared with the rest of the phone.
 * LightControl remaps the wheel system-wide, and someone who has given the wheel a job
 * everywhere else wants the volume keys to do what the wheel does here — a case the fixed
 * mapping could not express at all. So the mapping is data, the defaults are the old
 * behaviour, and [Controls.shutterSafe] is the one rule the data has to obey.
 */
enum class Binding(val label: String, val accepts: Accepts) {
    /**
     * **The two volume keys, bound as one thing.**
     *
     * They were separate until v2.45, and separating them was the mistake. A pair of keys either
     * side of a phone is a *dial* — the only reason to have two is that one means more and the
     * other means less — and the fixed mapping wasted that by pointing both of them at the same
     * press. Bound together they can carry a dial action, so the volume rocker walks the filters
     * in both directions, which is what somebody who has given the wheel away to LightControl
     * actually wants.
     *
     * They can still hold a press, and that is still the default: both keys fire it, exactly as
     * before.
     */
    VolumeKeys("Volume keys", Accepts.Either),
    WheelTurn("Turn the wheel", Accepts.Dial),
    WheelPressTurn("Press and turn", Accepts.Dial),
    WheelClick("Click the wheel", Accepts.Press),
    ;

    /** Which action this control has when nothing has been changed. */
    val default: String
        get() = when (this) {
            VolumeKeys -> PressAction.Shutter.name
            WheelTurn -> DialAction.Filter.name
            WheelPressTurn -> DialAction.Exposure.name
            WheelClick -> PressAction.Torch.name
        }

    /** Every action this control can be pointed at, in the order a list should show them. */
    fun options(): List<String> = when (accepts) {
        Accepts.Press -> PressAction.entries.map { it.name }
        Accepts.Dial -> DialAction.entries.map { it.name }
        // Dials first. A control that can be either is on this list because somebody wants the
        // dial — the presses are the thing it could already do.
        Accepts.Either ->
            DialAction.entries.filter { it != DialAction.Nothing }.map { it.name } +
                PressAction.entries.map { it.name }
    }

    /** How to name whatever is currently bound here. */
    fun labelOf(action: String?): String {
        val dial = DialAction.entries.firstOrNull { it.name == action }
        if (dial != null && accepts != Accepts.Press) return dial.label
        return PressAction.byName(action).label
    }
}

/** Which kind of action a control can carry. */
enum class Accepts { Press, Dial, Either }

/**
 * Something a press can be pointed at.
 *
 * [Exposure] and [Zoom] open the strip rather than nudging the value: a press is one event and a
 * strip is a control you then drag, which is the whole point of putting them on a button.
 */
enum class PressAction(val label: String) {
    Shutter("Shutter"),
    Torch("Torch"),
    FlipLens("Front / rear"),
    NextMode("Next mode"),
    Timer("Self timer"),

    /**
     * **`ExposureStrip`, not `Exposure`, and the suffix is load-bearing.**
     *
     * Since v2.45 a control can be offered both lists at once — the volume rocker can nudge the
     * exposure like a dial *or* open the strip like a press, and those are different things
     * somebody might genuinely want. The stored value is the constant's name, so while both
     * enums had a member called `Exposure` the two were indistinguishable on the way back in:
     * one list would show the option twice and reading it would always resolve to the dial.
     *
     * `Nothing` is still in both, deliberately. It means the same thing either way and resolves
     * to the same behaviour, so the ambiguity costs nothing — the list filters the duplicate out.
     */
    ExposureStrip("Exposure strip"),
    ZoomStrip("Zoom strip"),
    Nothing("Nothing"),
    ;

    companion object {
        fun byName(name: String?): PressAction =
            entries.firstOrNull { it.name == name } ?: Nothing
    }
}

/** Something a dial — a thing that reports notches — can be pointed at. */
enum class DialAction(val label: String) {
    Filter("Filter"),
    Exposure("Exposure"),
    Zoom("Zoom"),
    Nothing("Nothing"),
    ;

    companion object {
        fun byName(name: String?): DialAction =
            entries.firstOrNull { it.name == name } ?: Nothing
    }
}

object Controls {

    /**
     * **Is there still a way to take a photograph?**
     *
     * The one rule the mapping has to obey, and it is not a style question: this app has no
     * shutter button on the screen. Bind both volume keys to something else on a phone whose
     * camera key is being swallowed by an accessibility service — which is the ordinary state of
     * affairs for anyone running LightControl — and the camera has no shutter at all, with
     * nothing on the panel to press and no way to get back into settings to undo it except by
     * guessing.
     *
     * So: a shutter on the camera key counts, and a shutter on either volume key counts. If
     * neither is true the mapping is refused.
     *
     * @param cameraKeyWorks whether the camera button's events are reaching this app. See
     *   [CameraKeyAdvice], which answers it by looking for a service that binds the key.
     */
    fun shutterSafe(
        volume: PressAction,
        wheelClick: PressAction,
        cameraKeyWorks: Boolean,
    ): Boolean = cameraKeyWorks ||
        volume == PressAction.Shutter ||
        wheelClick == PressAction.Shutter

    /**
     * The same question asked of a stored value rather than a resolved action.
     *
     * A volume binding holding a *dial* action resolves to [PressAction.Nothing] through
     * [PressAction.byName], which is the right answer here and worth stating: pointing the volume
     * keys at the filters takes the shutter away from them just as surely as unbinding them does,
     * and the guard has to see that.
     */
    fun shutterSafe(volume: String?, wheelClick: String?, cameraKeyWorks: Boolean): Boolean =
        shutterSafe(PressAction.byName(volume), PressAction.byName(wheelClick), cameraKeyWorks)

    /**
     * A press action's name as v2.44 stored it, brought forward.
     *
     * `Exposure` and `Zoom` became `ExposureStrip` and `ZoomStrip` when the two action lists had
     * to share a namespace — see [PressAction.ExposureStrip]. Anything already on disk under the
     * old names would otherwise read as `Nothing`, which is the quiet kind of wrong: a wheel click
     * that used to open the exposure strip would simply stop doing anything.
     *
     * Only ever applied to a value that is known to be a *press*. A dial's `Exposure` is still
     * `Exposure` and must be left alone.
     */
    fun renamedPress(stored: String): String = when (stored) {
        "Exposure" -> PressAction.ExposureStrip.name
        "Zoom" -> PressAction.ZoomStrip.name
        else -> stored
    }
}
