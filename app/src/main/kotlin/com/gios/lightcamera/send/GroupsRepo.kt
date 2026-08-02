package com.gios.lightcamera.send

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The group conversations, read out of LightChat.
 *
 * **Why they can't come from anywhere else.** The address book has no representation of a
 * group iMessage — there is no contact row for "the boys", because the group is a chat room
 * on the Mac and not a person with a number. LightChat holds the only copy of that list on the
 * phone and publishes it read-only; this is the reading half. Without it the picker can offer
 * every individual and no group, which is the gap this closes.
 *
 * **Absent LightChat, this is empty and nothing else changes.** A phone without it, or with a
 * build too old to have the provider, returns no rows rather than an error, and the picker
 * simply shows contacts the way it always did. That is why every failure here is swallowed:
 * the provider not existing is the ordinary case on somebody else's phone, not a fault.
 */
class GroupsRepo(private val context: Context) {

    suspend fun load(): List<Group> = withContext(Dispatchers.IO) {
        val out = ArrayList<Group>()
        runCatching {
            context.contentResolver.query(CONTENT_URI, null, null, null, null)?.use { c ->
                // Columns by name, not by index. A provider is a different APK on its own
                // release cycle, and a column added to the middle of its projection would
                // silently shift everything after it — reading a guid as a title.
                val guidAt = c.getColumnIndex(COLUMN_GUID)
                val titleAt = c.getColumnIndex(COLUMN_TITLE)
                val sizeAt = c.getColumnIndex(COLUMN_PARTICIPANTS)
                val dateAt = c.getColumnIndex(COLUMN_LAST_DATE)
                if (guidAt < 0 || titleAt < 0) return@use
                while (c.moveToNext()) {
                    val guid = c.getString(guidAt)?.trim().orEmpty()
                    if (guid.isEmpty()) continue
                    val name = c.getString(titleAt)?.trim().orEmpty()
                    out += Group(
                        guid = guid,
                        // A group with no resolvable name still belongs in the list — it is a
                        // thread the user recognises by who is in it. Titling it by its member
                        // count beats dropping it, and beats showing a guid.
                        name = name.ifBlank { "Group" },
                        size = if (sizeAt >= 0) c.getInt(sizeAt) else 0,
                        lastDate = if (dateAt >= 0) c.getLong(dateAt) else 0L,
                    )
                }
            }
        }
        Groups.ordered(out)
    }

    private companion object {
        /**
         * LightChat's authority, hardcoded.
         *
         * It has to be: the whole point of a bridge is that the two apps are separate
         * packages, so there is no shared constant to import and no way to derive one from
         * `applicationId`. It is fixed on the far side for the same reason — see
         * LightChat's manifest.
         */
        val CONTENT_URI: Uri = Uri.parse("content://com.gios.lightchat.chats/chats")

        const val COLUMN_GUID = "guid"
        const val COLUMN_TITLE = "title"
        const val COLUMN_PARTICIPANTS = "participants"
        const val COLUMN_LAST_DATE = "last_date"
    }
}
