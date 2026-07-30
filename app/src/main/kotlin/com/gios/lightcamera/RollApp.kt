package com.gios.lightcamera

import android.app.Application

/**
 * Exists for one reason: to install [CrashLog] before anything else can fall over.
 *
 * The handler has to be in place before the first activity is created, and `Application.onCreate`
 * is the only hook early enough. Nothing else belongs here — no singletons, no eager work — since
 * every millisecond spent in here is a millisecond before the viewfinder appears.
 */
class RollApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
