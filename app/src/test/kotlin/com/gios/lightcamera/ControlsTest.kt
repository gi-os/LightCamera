package com.gios.lightcamera

import com.gios.lightcamera.hw.Accepts
import com.gios.lightcamera.hw.Binding
import com.gios.lightcamera.hw.Controls
import com.gios.lightcamera.hw.DialAction
import com.gios.lightcamera.hw.PressAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlsTest {

    /**
     * The mapping the app shipped with, spelled out here so that changing a default is a decision
     * somebody has to come and edit a test for.
     */
    @Test
    fun `the defaults are the mapping the app has always had`() {
        assertEquals(PressAction.Shutter.name, Binding.VolumeKeys.default)
        assertEquals(DialAction.Filter.name, Binding.WheelTurn.default)
        assertEquals(DialAction.Exposure.name, Binding.WheelPressTurn.default)
        assertEquals(PressAction.Torch.name, Binding.WheelClick.default)
    }

    @Test
    fun `what each control will accept`() {
        assertEquals(Accepts.Dial, Binding.WheelTurn.accepts)
        assertEquals(Accepts.Dial, Binding.WheelPressTurn.accepts)
        assertEquals(Accepts.Press, Binding.WheelClick.accepts)
        // The volume rocker is the only control that can be either, and the only one that can be
        // a dial without the wheel keys being mapped at all.
        assertEquals(Accepts.Either, Binding.VolumeKeys.accepts)
    }

    @Test
    fun `the volume list offers the dials first and then the presses`() {
        val options = Binding.VolumeKeys.options()
        assertEquals(DialAction.Filter.name, options.first())
        assertTrue(options.contains(PressAction.Shutter.name))
        // Exactly one Nothing. Both enums have one and they would otherwise both be listed,
        // which on a picker reads as a bug in the list rather than in the enums.
        assertEquals(1, options.count { it == "Nothing" })
    }

    @Test
    fun `a volume binding is labelled by whichever enum owns it`() {
        assertEquals(DialAction.Filter.label, Binding.VolumeKeys.labelOf(DialAction.Filter.name))
        assertEquals(PressAction.Shutter.label, Binding.VolumeKeys.labelOf(PressAction.Shutter.name))
        // A press-only control never reads a stored dial name as a dial.
        assertEquals(PressAction.Nothing.label, Binding.WheelClick.labelOf(DialAction.Filter.name))
    }

    /** A working camera key is a shutter, so nothing else has to be one. */
    @Test
    fun `anything goes while the camera key works`() {
        assertTrue(
            Controls.shutterSafe(
                volume = PressAction.ZoomStrip,
                wheelClick = PressAction.Torch,
                cameraKeyWorks = true,
            ),
        )
    }

    /**
     * The case this whole rule exists for: LightControl is swallowing the camera key, there is no
     * shutter button on the screen, and unbinding the last volume shutter would leave a camera that
     * cannot take a photograph and no way back into settings except guessing.
     */
    @Test
    fun `the last shutter cannot be given away when the camera key is swallowed`() {
        assertFalse(
            Controls.shutterSafe(
                volume = PressAction.FlipLens,
                wheelClick = PressAction.Torch,
                cameraKeyWorks = false,
            ),
        )
    }

    @Test
    fun `one shutter anywhere is enough`() {
        assertTrue(
            Controls.shutterSafe(PressAction.Shutter, PressAction.Nothing, cameraKeyWorks = false),
        )
        // The wheel counts too: a click bound to the shutter is a shutter.
        assertTrue(
            Controls.shutterSafe(PressAction.Nothing, PressAction.Shutter, cameraKeyWorks = false),
        )
    }

    /**
     * **Pointing the volume keys at the filters takes the shutter away from them.**
     *
     * The string overload is what the settings list asks, and it has to see a dial name for what
     * it is: `PressAction.byName("Filter")` is `Nothing`, so the rocker is no longer a shutter and
     * the guard must refuse it on a phone whose camera key is swallowed. This is the exact case
     * v2.45 introduced, and the one most likely to brick the camera by accident.
     */
    @Test
    fun `the volume keys cannot become a dial if they are the last shutter`() {
        assertFalse(
            Controls.shutterSafe(
                volume = DialAction.Filter.name,
                wheelClick = PressAction.Torch.name,
                cameraKeyWorks = false,
            ),
        )
        // With a working camera key it is fine, which is the ordinary case.
        assertTrue(
            Controls.shutterSafe(
                volume = DialAction.Filter.name,
                wheelClick = PressAction.Torch.name,
                cameraKeyWorks = true,
            ),
        )
        // ...or with the shutter moved onto the wheel click first, which is the way to do it.
        assertTrue(
            Controls.shutterSafe(
                volume = DialAction.Filter.name,
                wheelClick = PressAction.Shutter.name,
                cameraKeyWorks = false,
            ),
        )
    }

    /**
     * **The two lists share a namespace now, so their names have to be disjoint.**
     *
     * A control that can be either kind stores one string. While `Exposure` existed in both
     * enums, that string could not say which was meant — the list showed the option twice and
     * reading it back always resolved to the dial, silently turning "open the exposure strip"
     * into "nudge the exposure".
     */
    @Test
    fun `no name means two different things`() {
        val dials = DialAction.entries.map { it.name }.toSet()
        val presses = PressAction.entries.map { it.name }.toSet()
        // Nothing is deliberately in both: it means the same thing either way, and the Either
        // list filters the duplicate out.
        assertEquals(setOf("Nothing"), dials intersect presses)
        assertEquals(Binding.VolumeKeys.options().size, Binding.VolumeKeys.options().toSet().size)
    }

    /**
     * v2.44 stored `Exposure` and `Zoom` for the two strip presses. Left unmapped they now read as
     * `Nothing`, so a wheel click that opened the exposure strip would quietly stop doing anything.
     */
    @Test
    fun `the v2_44 press names are brought forward`() {
        assertEquals(PressAction.ExposureStrip.name, Controls.renamedPress("Exposure"))
        assertEquals(PressAction.ZoomStrip.name, Controls.renamedPress("Zoom"))
        assertEquals(PressAction.Torch.name, Controls.renamedPress("Torch"))
        assertEquals("Teleport", Controls.renamedPress("Teleport"))
    }

    @Test
    fun `an unknown stored name reads as nothing rather than throwing`() {
        assertEquals(PressAction.Nothing, PressAction.byName("Teleport"))
        assertEquals(DialAction.Nothing, DialAction.byName(null))
        assertEquals(PressAction.Shutter, PressAction.byName("Shutter"))
    }
}
