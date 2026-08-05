package com.gios.lightcamera.backup

import com.gios.light.common.sync.Contents
import com.gios.light.common.sync.FileStore
import com.gios.light.common.sync.LightSyncBackup

/**
 * What Roll hands to LightSync.
 *
 * **The photographs are not in here, and that is the whole design of it.** Roll writes its
 * pictures to `DCIM/Camera` through MediaStore and reads the whole of DCIM back — see
 * `media/MediaStoreRepo.kt`. Those files are shared storage, outside this app's sandbox and
 * outside anything a per-app backup provider is allowed to speak for. Pushing them through a
 * LAN blob store would be slow, enormous and duplicative: gigabytes of JPEG re-uploaded by an
 * app that does not own them, when whatever backs up your camera roll already has them. So this
 * provider carries only the facts about your photographs that exist nowhere else.
 *
 * Two stores, because they are worth different amounts:
 *
 *  - **`settings`** is the `camera` prefs file: the stamp style, colour behaviour, photo size,
 *    self-timer, autofocus and flash modes, the send picker's recent recipients — and the
 *    favourites list, which is the one that matters. A star is the only fact about a photograph
 *    that lives here rather than in MediaStore (`IS_FAVORITE` is effectively writable only by
 *    the system gallery), and it is keyed by display name precisely so it survives the photos
 *    being restored onto a different phone with different MediaStore ids.
 *  - **`film`** is the `roll` prefs file: which roll is loaded, how long it is, when it was
 *    started, and how many have been developed — the numbering that makes the next roll "roll
 *    12" rather than "roll 1".
 *
 * Left out on purpose, beyond the photographs themselves:
 *
 *  - `filesDir/rolls/` — the undeveloped frames of a loaded roll, full-size JPEGs plus a
 *    plain-text index. Same size argument as above, and a half-shot roll is not a thing you
 *    restore; `FilmRoll.readIndex` already drops index lines whose frame is missing, so the
 *    counters above come back consistent with an empty roll rather than broken.
 *  - The crash log in `filesDir` and the queued shake-to-report issues. Both are outbound
 *    diagnostics about one install, meaningless on the phone they would be restored onto.
 *  - Contacts and LightChat groups, which are read live from their own providers and were never
 *    stored here.
 */
class Backup : LightSyncBackup() {

    override fun label() = "Camera"

    override fun stores() = listOf(
        FileStore("settings", Contents(prefs = listOf("camera"))),
        FileStore("film", Contents(prefs = listOf("roll"))),
    )
}
