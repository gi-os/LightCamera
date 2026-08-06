package com.gios.lightcamera.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The characters in a photograph you already took.
 *
 * **Bundled ML Kit, and the distinction is the whole reason this compiles.** `qr/QrAnalyzer` says
 * ML Kit is unusable on this phone, and for the reader it names that is true: the *unbundled*
 * barcode and text models are delivered through Play Services, which LightOS does not ship, so
 * they bind and never answer. `com.google.mlkit:text-recognition` is the other artifact — the
 * model is inside the APK. It costs a few megabytes and needs nothing from the platform. QR
 * stayed on ZXing because ZXing is 500 kB and already worked; text recognition has no comparable
 * pure-Java option worth shipping.
 *
 * **This runs on a saved photograph, not on the live stream.** QR is a live analyzer because a
 * code is a thing you point at and want acted on within a second. Text is not that: you photograph
 * a page, a receipt, a business card, and the reading is something you go back to. Running a
 * recogniser on every preview frame would also cost far more than the viewfinder can spare on
 * this hardware, and would be answering a question nobody asked.
 */
object PageReader {

    private const val TAG = "PageReader"

    /**
     * Created once and never closed.
     *
     * The first call loads the model and takes noticeably longer than the rest, so a recogniser
     * per scan would pay that on every photograph. Closing it is the documented thing to do and
     * is skipped deliberately: the object lives as long as the process, and the alternative is
     * reloading a model to save memory the app is going to use again the moment you swipe.
     */
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Read [uri], or null if there was nothing to read.
     *
     * Null and empty are the same answer to the caller and both mean "no text here", which on a
     * photograph of a landscape is the correct and common result rather than a failure.
     *
     * `InputImage.fromFilePath` rather than a decoded bitmap: it reads the EXIF orientation and
     * hands the recogniser an upright image. A photograph taken with the phone on its side is the
     * normal case for anything with writing on it, and a recogniser given a sideways page returns
     * nothing at all rather than returning something wrong — which makes the bug look like the
     * feature simply not working.
     */
    suspend fun read(context: Context, uri: Uri): String? {
        val image = runCatching { InputImage.fromFilePath(context, uri) }
            .onFailure { Log.w(TAG, "cannot open $uri", it) }
            .getOrNull() ?: return null

        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text.takeIf { it.isNotBlank() }) }
                .addOnFailureListener { t ->
                    Log.w(TAG, "recognition failed", t)
                    cont.resume(null)
                }
            // Task has no cancel, so a cancelled scan simply lets the result be dropped. The work
            // is a few hundred milliseconds on one photograph, not something worth plumbing.
        }
    }
}
