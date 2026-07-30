package com.gios.lightcamera.send

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's address book, read once into memory.
 *
 * Two queries — phones and emails — rather than one over `Data.CONTENT_URI`, because the
 * two kinds have different label columns and the generic table hands back rows whose
 * meaning depends on a mimetype string you then have to switch on anyway.
 *
 * Read in full and filtered in memory rather than re-queried per keystroke
 * (`CONTENT_FILTER_URI` exists and is what a contacts app uses). An address book is a few
 * thousand rows at most and the whole list is wanted anyway for the picker's resting state;
 * a query per character would put a ContentResolver round trip inside the keyboard's
 * response time for no benefit.
 */
class ContactsRepo(private val context: Context) {

    suspend fun load(): List<Recipient> = withContext(Dispatchers.IO) {
        val rows = ArrayList<Recipients.Row>(256)
        rows += readPhones()
        rows += readEmails()
        Recipients.merge(rows).take(Recipients.MAX)
    }

    private fun readPhones(): List<Recipients.Row> = query(
        uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        columns = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
        ),
        kind = Address.Kind.Phone,
    ) { type, label ->
        // The address book stores a type code and only stores free text for CUSTOM, so the
        // human label has to be resolved through the platform's own string table — which is
        // also how it comes out localised.
        runCatching {
            ContactsContract.CommonDataKinds.Phone
                .getTypeLabel(context.resources, type, label)
                .toString()
        }.getOrDefault("")
    }

    private fun readEmails(): List<Recipients.Row> = query(
        uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        columns = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.TYPE,
            ContactsContract.CommonDataKinds.Email.LABEL,
            ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY,
        ),
        kind = Address.Kind.Email,
    ) { type, label ->
        runCatching {
            ContactsContract.CommonDataKinds.Email
                .getTypeLabel(context.resources, type, label)
                .toString()
        }.getOrDefault("")
    }

    /**
     * Both queries have the same shape — id, name, address, type, label — so they share one
     * cursor loop. Wrapped in `runCatching`: a revoked permission surfaces as a
     * SecurityException from the resolver rather than as a null cursor, and an empty picker
     * that explains itself beats a crash while sending a photograph.
     */
    private inline fun query(
        uri: android.net.Uri,
        columns: Array<String>,
        kind: Address.Kind,
        crossinline labelOf: (Int, String?) -> String,
    ): List<Recipients.Row> {
        val out = ArrayList<Recipients.Row>()
        runCatching {
            context.contentResolver.query(uri, columns, null, null, null)?.use { c: Cursor ->
                val idAt = c.getColumnIndexOrThrow(columns[0])
                val nameAt = c.getColumnIndexOrThrow(columns[1])
                val addressAt = c.getColumnIndexOrThrow(columns[2])
                val typeAt = c.getColumnIndexOrThrow(columns[3])
                val labelAt = c.getColumnIndexOrThrow(columns[4])
                val primaryAt = c.getColumnIndexOrThrow(columns[5])
                while (c.moveToNext()) {
                    val address = c.getString(addressAt) ?: continue
                    if (address.isBlank()) continue
                    val type = c.getInt(typeAt)
                    out += Recipients.Row(
                        contactId = c.getLong(idAt),
                        name = c.getString(nameAt) ?: "",
                        address = address,
                        kind = kind,
                        label = labelOf(type, c.getString(labelAt)),
                        // The address book's own idea of which one to use, and whether this is a
                        // mobile. Without them the "preferred" address is whichever row the
                        // provider returned first — insertion order — so a contact whose landline
                        // was typed in before their mobile would be sent a photograph at the
                        // landline, with nothing in the UI to say so or to change it.
                        superPrimary = c.getInt(primaryAt) != 0,
                        mobile = kind == Address.Kind.Phone &&
                            type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                    )
                }
            }
        }
        return out
    }
}
