package com.gios.lightcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gios.lightcamera.Chrome
import com.gios.lightcamera.Colour
import com.gios.lightcamera.CrashLog
import com.gios.lightcamera.SelfTimer
import com.gios.lightcamera.camera.AfMode
import com.gios.lightcamera.camera.FrameAspect
import com.gios.lightcamera.hw.CameraKeyAdvice
import com.gios.lightcamera.hw.LightKeys
import com.gios.lightcamera.hw.WheelScroll
import com.gios.lightcamera.ui.theme.LightIcons
import com.gios.lightcamera.ui.theme.LightText
import com.gios.lightcamera.ui.theme.LightTextVariant
import com.gios.lightcamera.ui.theme.LightThemeTokens
import com.gios.lightcamera.ui.theme.lightClickable

/**
 * Settings, and the roll.
 *
 * Every row is a value you cycle by tapping it rather than a switch or a dialog, which is
 * how LightOS does settings and also the only shape that stays legible at this width. The
 * roll lives at the bottom because loading and developing are the two most consequential
 * things in the app and the top of a list is no place for them.
 */
@Composable
fun SettingsScreen(vm: CameraViewModel, onClose: () -> Unit) {
    val colours = LightThemeTokens.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val scroll = rememberScrollState()
    WheelScroll(scroll)

    val aspect by vm.prefs.aspect.collectAsState()
    val chrome by vm.prefs.chrome.collectAsState()
    val afMode by vm.prefs.afMode.collectAsState()
    val facePriority by vm.prefs.facePriority.collectAsState()
    val timer by vm.prefs.timer.collectAsState()
    val sounds by vm.prefs.sounds.collectAsState()
    val colour by vm.prefs.colour.collectAsState()
    val sendChat by vm.prefs.sendToLightChat.collectAsState()
    val wheel by vm.prefs.wheelEnabled.collectAsState()
    val rollLength by vm.prefs.rollLength.collectAsState()
    val roll by vm.roll.collectAsState()
    val facesSupported by vm.engine.facesSupported.collectAsState()

    var confirmDiscard by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                "SETTINGS",
                LightTextVariant.Superfine,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            ChromeIcon(icon = LightIcons.Close, onClick = onClose)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
        ) {
            Section("Frame")
            Setting("Shape", aspect.label) {
                val all = FrameAspect.entries
                vm.prefs.setAspect(all[(all.indexOf(aspect) + 1) % all.size])
            }
            Setting("Grid", chrome.label) {
                val all = Chrome.entries
                vm.prefs.setChrome(all[(all.indexOf(chrome) + 1) % all.size])
            }
            Note(
                "The viewfinder fills the screen and the sensor is 4:3, so the photograph keeps a little more than you saw — at the top and bottom of the frame.",
            )

            Section("Colour")
            Setting("Show", colour.label) {
                val all = Colour.entries
                vm.prefs.setColour(all[(all.indexOf(colour) + 1) % all.size])
            }
            Note(
                if (ColorMode.granted(context)) {
                    "The panel is a full-colour AMOLED — Light's black and white is the accessibility daltonizer pinned to monochromacy. Roll lifts it while the camera is up and puts it back when you leave."
                } else {
                    "Needs one adb grant, then the viewfinder is in colour:\n\nadb shell pm grant com.gios.lightcamera android.permission.WRITE_SECURE_SETTINGS\n\nUntil then everything stays grey, which is harmless — the write is simply refused."
                },
            )

            Section("Sending")
            Setting("Send button", if (sendChat) "Use LightChat" else "Off") {
                vm.prefs.setSendToLightChat(!sendChat)
            }
            Note(
                "Off, the send button in the viewer is dead. On, it hands the photograph straight to LightChat with no share sheet in between — one destination, named explicitly.",
            )

            Section("Focus")
            Setting("Mode", if (afMode == AfMode.Single) "Single" else "Continuous") {
                vm.prefs.setAfMode(if (afMode == AfMode.Single) AfMode.Continuous else AfMode.Single)
            }
            Setting(
                label = "Faces",
                value = when {
                    !facesSupported -> "Unavailable"
                    facePriority -> "Priority"
                    else -> "Ignore"
                },
                enabled = facesSupported,
            ) {
                vm.prefs.setFacePriority(!facePriority)
            }
            Note(
                if (facesSupported) {
                    "Half press the camera button to focus on the nearest face and hold it. Press through to take the photograph. The mark closes into a box, and beeps, when the lens has it."
                } else {
                    "This camera doesn't report faces, so the half press focuses on the centre of the frame."
                },
            )

            Section("Shutter")
            Setting("Self timer", timer.label) {
                val all = SelfTimer.entries
                vm.prefs.setTimer(all[(all.indexOf(timer) + 1) % all.size])
            }
            Setting("Sounds", if (sounds) "Focus beep" else "Off") {
                vm.prefs.setSounds(!sounds)
            }
            Setting("Wheel", if (wheel) "Zoom / EV" else "Off") {
                vm.prefs.setWheelEnabled(!wheel)
            }
            Note(
                if (LightKeys.wheelLabelsPresent()) {
                    "Turn the wheel to zoom. Hold it in and turn for exposure. Click it for the torch. Either volume key is also a shutter."
                } else {
                    "This build doesn't map the wheel keys, so turning it may do nothing. Either volume key is a shutter."
                },
            )
            val keyProblem = remember { CameraKeyAdvice.problem(context) }
            if (keyProblem != null) {
                // Inverted, because a dead shutter is not a footnote.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(colours.content)
                        .padding(10.dp),
                ) {
                    LightText(
                        text = keyProblem,
                        variant = LightTextVariant.Detail,
                        color = colours.background,
                    )
                }
            } else {
                Note(
                    "There is no shutter button on screen, because the phone has one on its side. If the camera button ever does nothing, an accessibility service is swallowing it.",
                )
            }

            Section("Film")
            if (roll == null) {
                Setting("Frames per roll", "$rollLength") {
                    vm.prefs.setRollLength(
                        when (rollLength) {
                            12 -> 24
                            24 -> 36
                            else -> 12
                        },
                    )
                }
                Action("Load a roll") { vm.loadRoll(); onClose() }
                Note(
                    "With a roll loaded, photographs go onto the roll instead of into the gallery. No preview, no review — just a counter — until you develop it.",
                )
            } else {
                val loaded = roll
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText("Roll ${loaded?.number}", LightTextVariant.Copy)
                    Spacer(Modifier.weight(1f))
                    LightText(
                        "${loaded?.shot} of ${loaded?.length}",
                        LightTextVariant.Copy,
                        lighten = true,
                    )
                }
                RollCounter(roll = loaded, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                Action(if ((loaded?.shot ?: 0) == 0) "Unload" else "Develop") {
                    vm.developRoll()
                    onClose()
                }
                if ((loaded?.shot ?: 0) > 0) {
                    Action(
                        if (confirmDiscard) "Tap again to throw the roll away" else "Discard",
                        lighten = true,
                    ) {
                        if (confirmDiscard) {
                            vm.discardRoll()
                            confirmDiscard = false
                            onClose()
                        } else {
                            confirmDiscard = true
                        }
                    }
                }
                Note("Developing writes every frame into the camera roll, each keeping the time it was taken.")
            }

            val crash = remember { CrashLog.last(context) }
            if (crash != null) {
                Section("Last crash")
                Note(
                    "Roll fell over. The trace is below — the first few lines are the ones that matter. Tap to clear it.",
                )
                var cleared by remember { mutableStateOf(false) }
                if (!cleared) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .background(colours.rule)
                            .lightClickable {
                                CrashLog.clear(context)
                                cleared = true
                            }
                            .padding(8.dp),
                    ) {
                        LightText(
                            text = crash.lineSequence().take(14).joinToString("\n"),
                            variant = LightTextVariant.Micro,
                        )
                    }
                }
            }

            Section("About")
            Note(
                "Roll — a camera for the Light Phone III. Filters are AGSL shaders applied to the live preview and to the photograph by the same code, so the file matches the frame.",
            )
            Box(Modifier.height(24.dp))
            LightText(
                "github.com/gi-os/LightCamera",
                LightTextVariant.Micro,
                lighten = true,
            )
            Box(Modifier.height(8.dp))
            LightText(
                "Icons and design tokens from lightphone/light-sdk, MIT.",
                LightTextVariant.Micro,
                color = colours.contentFaint,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    LightText(
        text = title.uppercase(),
        variant = LightTextVariant.Superfine,
        lighten = true,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun Setting(
    label: String,
    value: String,
    enabled: Boolean = true,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(enabled = enabled) { onTap() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(label, LightTextVariant.Copy, lighten = !enabled)
        Spacer(Modifier.weight(1f))
        LightText(value, LightTextVariant.Copy, lighten = true)
    }
}

@Composable
private fun Action(label: String, lighten: Boolean = false, onTap: () -> Unit) {
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

@Composable
private fun Note(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )
}
