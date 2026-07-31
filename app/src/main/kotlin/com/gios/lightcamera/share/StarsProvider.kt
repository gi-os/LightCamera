package com.gios.lightcamera.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.gios.lightcamera.PrefsFile

/**
 * The photographs you starred, offered to the rest of the collection.
 *
 * Read-only, and deliberately the smallest useful thing: a list of file names. It exists because a
 * star is the one fact about a photograph that **only this app knows** — everything else another app
 * might want is already in MediaStore, which is why LightNotebook can show your pictures on its
 * calendar without any bridge at all. A star has nowhere else to live: `IS_FAVORITE` exists in
 * MediaStore but is effectively writable only by the system gallery, so this app keeps its own list
 * and has to hand it over deliberately.
 *
 * **Names, not ids.** A MediaStore id is a row number — rescan the volume, move a file, restore a
 * backup, and the same photograph has a different one. The stars are stored by name for exactly that
 * reason, so the same is served here, and the caller matches on `DISPLAY_NAME`.
 *
 * No permission on it. It reveals which of your own photographs you liked, to any app that asks by
 * name — which on a phone with one user and a hand-picked set of apps is a smaller risk than the
 * alternative, which is another signature check to maintain against a keystore per app. It is
 * `exported` and read-only, and there is nothing here that is not already on the device.
 */
class StarsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val context = context ?: return MatrixCursor(arrayOf(COLUMN_NAME))
        val cursor = MatrixCursor(arrayOf(COLUMN_NAME))
        // Read straight from the file rather than through Prefs: a provider can be queried while no
        // part of the app is running, and building the whole Prefs object with its dozen state flows
        // to answer a list of strings would be absurd.
        val stars = runCatching {
            context.getSharedPreferences(PrefsFile.NAME, android.content.Context.MODE_PRIVATE)
                .getStringSet(PrefsFile.FAVOURITES, null)
                .orEmpty()
        }.getOrDefault(emptySet())
        stars.sorted().forEach { cursor.addRow(arrayOf(it)) }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.$AUTHORITY_SUFFIX.star"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY_SUFFIX = "stars"

        /** The file name of a starred photograph, matching MediaStore's `DISPLAY_NAME`. */
        const val COLUMN_NAME = "display_name"
    }
}
