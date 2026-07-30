package com.gios.lightcamera

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gios.lightcamera.hw.LightControls
import com.gios.lightcamera.hw.LocalWheelBus
import com.gios.lightcamera.hw.ShutterRelease
import com.gios.lightcamera.hw.WheelBus
import com.gios.lightcamera.ui.CameraViewModel
import com.gios.lightcamera.ui.ColorMode
import com.gios.lightcamera.ui.Shell
import com.gios.lightcamera.ui.theme.LightCameraTheme

/**
 * One activity, because a camera is one thing.
 *
 * It owns two responsibilities that can only live here:
 *
 *  - **The physical controls.** [dispatchKeyEvent] is the only place that sees the camera
 *    button and the wheel before the view hierarchy does. The two-stage release is a state
 *    machine ([ShutterRelease]) rather than a pair of key handlers, because the two keys
 *    arrive in an unpredictable order.
 *  - **Being the phone's camera.** Launched with `IMAGE_CAPTURE`, the app has to take one
 *    photograph, write it where the caller asked, and get out of the way. That is what makes
 *    this installable as the default camera rather than merely as an app with a viewfinder.
 */
class MainActivity : ComponentActivity() {

    private val wheel = WheelBus()
    private var controls: LightControls? = null
    private var viewModel: CameraViewModel? = null

    /**
     * Launched, or brought forward by the camera key.
     *
     * `singleTop` means the second press lands here rather than in [onCreate], with the activity still
     * showing whatever was on screen when you left. The viewfinder is what a camera button asks for.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel?.onCameraKeyLaunch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge to edge, and never dim while framing a shot: a camera that sleeps on a tripod
        // is a camera you stop using on a tripod.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // The bars are hidden, not just drawn behind. The stock camera's picture starts at the
        // very top edge of the panel, and on a 3.92" screen a status bar is about four percent
        // of the viewfinder spent telling you the time. Swipe from an edge to get them back.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        val captureOutput = intentCaptureOutput()
        val isCaptureRequest = captureOutput != null || isCaptureAction()

        setContent {
            LightCameraTheme {
                val vm: CameraViewModel = viewModel()
                viewModel = vm

                LaunchedEffect(vm) {
                    vm.captureRequestOutput = captureOutput
                    controls = LightControls(
                        activity = this@MainActivity,
                        wheel = wheel,
                        shutter = ShutterRelease(
                            onHalfPress = { vm.engine.halfPress() },
                            onFullPress = { vm.shoot() },
                            onRelease = { vm.engine.releaseFocus() },
                        ),
                        onTorchToggle = { vm.engine.toggleTorch() },
                        onVolumeShutter = { vm.shoot() },
                    )
                }

                // Somebody else's photograph. Hand it back and leave.
                LaunchedEffect(vm) {
                    vm.captureRequestDone.collect { ok ->
                        finishCaptureRequest(ok, captureOutput)
                    }
                }

                CompositionLocalProvider(LocalWheelBus provides wheel) {
                    Shell(vm = vm, captureRequest = isCaptureRequest)
                }
            }
        }
    }

    /**
     * Greyscale comes back the moment the app is not in front, and colour returns if it comes
     * back with the viewfinder still open.
     *
     * The daltonizer is a display-wide setting, so leaving it lifted while the user is
     * somewhere else on the phone would quietly turn the whole of LightOS colour — which is
     * their setting to make, not ours. `holders` survives the stop, so this is only about the
     * foreground and not about closing anything.
     */
    override fun onStart() {
        super.onStart()
        ColorMode.onAppVisible(this)
    }

    override fun onStop() {
        super.onStop()
        ColorMode.onAppHidden(this)
    }

    /**
     * The wheel and the camera button.
     *
     * `DecorView` hands the event to the window callback — this — before walking the views,
     * so returning true here beats anything focused. Nothing else in the app listens for
     * keys, which is why there is no arbitration to do.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controls?.dispatch(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * Where an `IMAGE_CAPTURE` caller wants the photograph.
     *
     * `EXTRA_OUTPUT` is the documented way and the one every serious caller uses. A caller
     * that omits it is asking for a thumbnail in the result `Intent`, which this refuses:
     * a bitmap in an `Intent` extra has been a `TransactionTooLargeException` waiting to
     * happen since 2010, and the callers that rely on it are asking for a photograph they can
     * barely see.
     */
    private fun intentCaptureOutput(): Uri? {
        if (!isCaptureAction()) return null
        @Suppress("DEPRECATION")
        return intent?.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)
    }

    private fun isCaptureAction(): Boolean = when (intent?.action) {
        MediaStore.ACTION_IMAGE_CAPTURE, "android.media.action.IMAGE_CAPTURE_SECURE" -> true
        else -> false
    }

    private fun finishCaptureRequest(ok: Boolean, output: Uri?) {
        if (!ok) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val result = Intent()
        if (output != null) {
            result.data = output
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
