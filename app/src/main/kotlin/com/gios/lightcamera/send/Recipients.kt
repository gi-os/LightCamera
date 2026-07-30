package com.gios.lightcamera.send

/**
 * One way to reach one person: a number or an email address, as it came out of the address
 * book, plus the form used to compare and de-duplicate it.
 *
 * [key] exists because the same phone is written five ways in one address book —
 * `+1 (212) 555-0148`, `212-555-0148`, `2125550148` — and a picker that lists all of them is
 * asking the user to know which spelling the messaging app prefers.
 */
data class Address(
    val raw: String,
    val key: String,
    val kind: Kind,
    /** "Mobile", "Home", "Work"… as the address book labels it. Blank when it doesn't. */
    val label: String = "",
) {
    enum class Kind { Phone, Email }
}

/** A person, and every way to reach them, best first. */
data class Recipient(
    val id: Long,
    val name: String,
    val addresses: List<Address>,
) {
    val primary: Address? get() = addresses.firstOrNull()

    /**
     * The address a photograph should go to: a phone if there is one, otherwise the best
     * remaining. Kept distinct from [primary] because the two answer different questions — a
     * contact can have a starred email address and a mobile, and the mobile is what receives a
     * picture.
     */
    val forPhoto: Address?
        get() = addresses.firstOrNull { it.kind == Address.Kind.Phone } ?: addresses.firstOrNull()

    /** What the row's second line says. */
    val subtitle: String
        get() = primary?.let { a ->
            if (a.label.isBlank()) a.raw else a.label + " · " + a.raw
        } ?: ""
}

/**
 * Address-book handling, with no Android in it.
 *
 * Split out from the ContentResolver query for one reason: normalising a phone number and
 * deciding whether a search matches a name are the two things here that are easy to get
 * subtly wrong, and they're the two things a unit test can hold still. The cursor loop
 * around them is boring by comparison.
 */
object Recipients {

    /** Above this, the picker is a search box rather than a list — but the list still has to end. */
    const val MAX = 2000

    /**
     * The comparable form of an address.
     *
     * Phones keep their digits and nothing else, then the last [PHONE_MATCH_DIGITS]. Comparing
     * whole numbers fails on exactly the pairs a user considers identical: a contact saved as
     * `212-555-0148` and the same person's `+1 212 555 0148` differ by a country code that only
     * one of them bothered to write. Comparing the tail is what every phone's own dialer does,
     * and the false-positive it risks — two different numbers agreeing in their last seven
     * digits — needs a coincidence that doesn't happen inside one address book.
     *
     * Emails lowercase and trim. Nothing else: the local part is case-sensitive per the RFC,
     * dot-stripping is a Gmail-only rule, and guessing either would merge two real addresses.
     */
    fun key(raw: String, kind: Address.Kind): String = when (kind) {
        Address.Kind.Email -> raw.trim().lowercase()
        Address.Kind.Phone -> {
            val digits = raw.filter { it.isDigit() }
            if (digits.length <= PHONE_MATCH_DIGITS) digits else digits.takeLast(PHONE_MATCH_DIGITS)
        }
    }

    private const val PHONE_MATCH_DIGITS = 10

    /**
     * Collapses raw address-book rows into people.
     *
     * Rows arrive one per address, so a contact with a mobile, a landline and two emails is four
     * rows with the same [Row.contactId] — grouping them is what makes the picker a list of
     * people rather than a list of phone numbers. Within a person, addresses are de-duplicated
     * by [key] and ordered phones-before-emails, since this exists to send a photograph to a
     * phone and an email address is the fallback.
     *
     * A row with no name is kept and titled by its own address: an unsaved number you texted
     * once is still somebody you might send a photograph to, and dropping it would make the
     * picker quietly incomplete.
     */
    fun merge(rows: List<Row>): List<Recipient> {
        val byContact = LinkedHashMap<Long, MutableList<Row>>()
        for (row in rows) {
            if (row.address.isBlank()) continue
            byContact.getOrPut(row.contactId) { mutableListOf() }.add(row)
        }
        val out = ArrayList<Recipient>(byContact.size)
        for ((id, group) in byContact) {
            val seen = HashSet<String>()
            // Best first, and "best" is decided here rather than by the order the provider
            // happened to return rows in: the user's own default, then mobiles, then any other
            // phone, then email. A photograph sent to a landline is a photograph nobody sees.
            val addresses = group
                .sortedWith(
                    compareBy(
                        { if (it.superPrimary) 0 else 1 },
                        { if (it.kind == Address.Kind.Phone) 0 else 1 },
                        { if (it.mobile) 0 else 1 },
                    ),
                )
                .mapNotNull { row ->
                    val k = key(row.address, row.kind)
                    if (k.isBlank() || !seen.add(k)) {
                        null
                    } else {
                        Address(raw = row.address.trim(), key = k, kind = row.kind, label = row.label.trim())
                    }
                }
            if (addresses.isEmpty()) continue
            val name = group.firstNotNullOfOrNull { it.name.trim().ifBlank { null } }
                ?: addresses.first().raw
            out.add(Recipient(id = id, name = name, addresses = addresses))
        }
        // Case-insensitive, so `alex` doesn't sort after `Zoe` the way a raw String compare puts
        // every lowercase name below every uppercase one.
        return out.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** One address-book row, as read from the cursor. */
    data class Row(
        val contactId: Long,
        val name: String,
        val address: String,
        val kind: Address.Kind,
        val label: String = "",
        /** The address book's `IS_SUPER_PRIMARY` — the user's own choice of default. */
        val superPrimary: Boolean = false,
        /** A phone typed as a mobile. Worth preferring: it's the one that receives pictures. */
        val mobile: Boolean = false,
    )

    /**
     * Whether [recipient] matches what's been typed.
     *
     * Two searches in one box, because on a phone the same box gets both. Letters match the
     * name, at the **start of any word** rather than anywhere inside it — an infix match on a
     * long address book returns half of it for a two-letter query, and "an" hitting "Joanna"
     * is noise. Digits match the address, comparing digits only, so `5550148` finds
     * `+1 (212) 555-0148` without the user reproducing its punctuation.
     *
     * A query with both (`alex 555`) has to satisfy both halves, which is how you separate two
     * people with the same first name.
     */
    fun matches(recipient: Recipient, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val digits = trimmed.filter { it.isDigit() }
        val letters = trimmed.filter { !it.isDigit() }.trim()
        if (digits.isNotEmpty()) {
            val hit = recipient.addresses.any { a ->
                a.kind == Address.Kind.Phone && a.raw.filter { it.isDigit() }.contains(digits)
            }
            if (!hit) return false
        }
        if (letters.isNotEmpty()) {
            val hit = nameMatches(recipient.name, letters) ||
                recipient.addresses.any { it.kind == Address.Kind.Email && it.raw.startsWith(letters, ignoreCase = true) }
            if (!hit) return false
        }
        return true
    }

    private fun nameMatches(name: String, letters: String): Boolean {
        val needle = letters.lowercase()
        if (name.startsWith(needle, ignoreCase = true)) return true
        // Word starts. Split on anything that isn't a letter or digit so "mary-jane" and
        // "o'brien" both offer their second word.
        var atWordStart = false
        for (i in name.indices) {
            val c = name[i]
            val isWordChar = c.isLetterOrDigit()
            if (!isWordChar) { atWordStart = true; continue }
            if (atWordStart) {
                atWordStart = false
                if (name.startsWith(needle, startIndex = i, ignoreCase = true)) return true
            }
        }
        return false
    }

    /**
     * The picker's order: the people sent to recently, in that order, then everybody else.
     *
     * Recents are held by address [key] rather than by contact id, because a contact id is
     * local to one address-book database and does not survive a restore — the list would empty
     * itself after a phone swap, which is the same reasoning behind keying starred photographs
     * by filename.
     *
     * A recent whose contact has since been deleted is dropped rather than shown as a bare
     * number: the point of the list is recognising a face's name in it.
     */
    fun ordered(all: List<Recipient>, recentKeys: List<String>): Ordered {
        if (recentKeys.isEmpty()) return Ordered(emptyList(), all)
        val byKey = HashMap<String, Recipient>()
        for (r in all) for (a in r.addresses) byKey.putIfAbsent(a.key, r)
        val recent = ArrayList<Recipient>()
        val taken = HashSet<Long>()
        for (k in recentKeys) {
            val hit = byKey[k] ?: continue
            if (taken.add(hit.id)) recent.add(hit)
        }
        return Ordered(recent, all.filter { it.id !in taken })
    }

    data class Ordered(val recent: List<Recipient>, val rest: List<Recipient>)

    /**
     * Adds [key] to the front of the recents list, moving it if it's already there.
     *
     * Capped at [RECENTS] — a recents list long enough to scroll is just the address book
     * again, and the whole value of it is that the person you want is on screen already.
     */
    fun remember(existing: List<String>, key: String, limit: Int = RECENTS): List<String> {
        if (key.isBlank()) return existing
        return (listOf(key) + existing.filter { it != key }).take(limit)
    }

    const val RECENTS = 6
}
