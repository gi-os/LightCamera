# Keep rules for Roll, with R8 full mode on.
#
# Every rule below names the mechanism that needs it. There is deliberately no blanket
# `-keep class com.gios.lightcamera.**`: that would keep the whole app and leave only the
# dead-code pass doing any work, which is the opposite of the reason full mode is on.
#
# The one thing to remember when something breaks in a release build and not a debug one: in
# full mode a `-keep` on a class no longer implies keeping its members. Constructors in
# particular have to be spelled out.

# ---------------------------------------------------------------- crash reports

# Shake-to-report is only useful if the stack trace in the issue names real files and lines.
# Without these a report reads `a.a.a(Unknown Source)` and there is nothing to triage.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------- settings persisted by name

# Every enum here is written to SharedPreferences as `value.name` and read back by comparing
# `it.name` against the stored string (Prefs.kt: PhotoSize, Chrome, StampStyle, Colour,
# SelfTimer, plus FlashMode/AfMode from camera/ and RollScope from media/). R8 renames enum
# constants and rewrites the name passed to the Enum constructor along with them, so after
# minification `it.name` returns `a` and nothing ever matches — every setting silently reverts
# to its default on the first release build. Keeping the constants keeps the strings.
#
# Scoped to enums, not to classes: the enum bodies are still shrunk and the rest of the app is
# untouched.
-keepclassmembers enum com.gios.lightcamera.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------- manifest-named components

# These four are instantiated by the framework from a string in the manifest, so nothing in the
# code refers to them and full mode sees no allocation. aapt2 does emit keep rules from the
# manifest, but only for the class — not for the no-arg constructor the framework calls, which
# full mode is free to remove. `.backup.Backup` is additionally covered by light-common's own
# consumer rules; it is listed here because the constructor is the part that matters.
-keep class com.gios.lightcamera.RollApp { public <init>(); }
-keep class com.gios.lightcamera.MainActivity { public <init>(); }
-keep class com.gios.lightcamera.share.StarsProvider { public <init>(); }
-keep class com.gios.lightcamera.backup.Backup { public <init>(); }

# ---------------------------------------------------------------- CameraX

# `ProcessCameraProvider.getInstance(context)` is called with no explicit CameraXConfig, so
# CameraX resolves the camera2 backend by name: it does `Class.forName` on this class and then
# `getDeclaredMethod("defaultConfig")` on it. Nothing links to either, so full mode removes
# both and the app comes up with "CameraProvider is not ready" and a black viewfinder.
-keep class androidx.camera.camera2.Camera2Config {
    public static androidx.camera.core.CameraXConfig defaultConfig();
}

# camera-video reaches the hardware through the same Camera2Config above, so it needs no rule
# of its own. Worth knowing that video mode is only bound after the user switches to it: a
# smoke test of the viewfinder does not exercise it, so a release build has to be tried there
# by hand.

# ---------------------------------------------------------------- ML Kit text recognition
#
# The notes below used to say there was no ML Kit in this app. There is now, for reading the
# words off a photograph on the roll — the *bundled* text recogniser, whose model is in the APK
# rather than behind Play Services. (QR is still ZXing, and `ocr/PageReader.kt` says why.)
#
# ML Kit finds its own implementation the way Firebase does: each artifact declares a
# `ComponentRegistrar` in its manifest and the runtime instantiates it by class name. Nothing in
# our code refers to those classes, so full mode removes them and `TextRecognition.getClient`
# throws at the first press of TEXT — on a photograph already taken, which is the worst place to
# discover it, and it fails looking like "this picture has no words in it".
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.mlkit.common.internal.** { *; }
-keep class com.google.mlkit.vision.text.internal.** { *; }
-keep class com.google.mlkit.vision.text.latin.** { *; }

# The recogniser is native underneath and the JNI lookup is by name, so the bridge classes cannot
# be renamed even though nothing Java-side would notice.
-keepclasseswithmembernames class com.google.mlkit.** {
    native <methods>;
}
-dontwarn com.google.mlkit.**

# ---------------------------------------------------------------- notes, so nobody adds a rule

# Face detection needs nothing. It is the *hardware* detector, read through Camera2Interop and
# `CaptureResult.STATISTICS_FACES` — see camera/CameraEngine.kt, which says why ML Kit was not
# used. There is no model loaded by name and no ML Kit on the classpath at all, so the usual
# `-keep class com.google.mlkit.**` that every camera app carries would keep nothing here.
#
# The AGSL filters are shader source in Kotlin string constants, compiled by the platform at
# runtime. Strings are not renamed, and `Filters.byId` matches a stored id against another
# string constant — no class is ever selected by name, so filters need no rule either.
#
# The report queue is JSON built with explicit `JSONObject.put("field", …)` calls rather than
# field-name reflection, so its data classes may be renamed freely.
#
# `KeyEvent.keyCodeFromString` in light-common resolves Light's key labels through a native
# platform table. That is not reflection into any kept code.
#
# `ocr/TextScan` is plain string work with no reflection, and `TextSheet` reaches it by ordinary
# calls. The extraction patterns are Kotlin `Regex` literals, not class names.

# ---------------------------------------------------------------- third party

# OkHttp compiles against optional TLS providers that are not on the classpath here (Roll ships
# no Conscrypt and no Bouncy Castle). OkHttp ships rules for this in its own jar; these are
# repeated because a missing-class warning is a hard error in full mode and a red release build
# for a dependency that is working correctly is a bad afternoon.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
