package com.gios.lightcamera.send

/**
 * A group conversation, as somewhere a photograph can go.
 *
 * **Deliberately not a [Recipient].** A person is a name with a set of addresses and the
 * picker's job is choosing between them; a group is a name with a guid and there is nothing
 * to choose. Folding the two into one type would mean a [Recipient] whose `addresses` list is
 * empty and whose `forPhoto` is null — which is exactly the shape the picker already uses to
 * mean "there is no way to reach this person", and every check that guards against sending
 * into the void would have to learn a second meaning for it. Two types, one list, one branch
 * at the send.
 */
data class Group(
    /**
     * The chat-room guid, straight from LightChat. **Opaque.** It looks like
     * `iMessage;+;chat684…` and nothing here parses it — the shape is the server's business,
     * and a reader that depends on it breaks the first time BlueBubbles changes how a room is
     * named.
     */
    val guid: String,
    /** What LightChat's conversation list calls it, with handles already resolved to names. */
    val name: String,
    /** How many people are in it. Zero when LightChat didn't say. */
    val size: Int,
    /** Epoch millis of the last activity, for ordering. */
    val lastDate: Long,
) {
    /** The row's second line. */
    val subtitle: String
        get() = when {
            size > 1 -> "$size people"
            else -> "Group"
        }
}

/**
 * Ordering and filtering for groups, with no Android in it.
 *
 * Same reasoning as [Recipients]: the cursor loop is boring and the decisions about what the
 * user sees are not, so the decisions live somewhere a test can hold them still.
 */
object Groups {

    /**
     * How many groups the picker will show at rest.
     *
     * A cap rather than the whole list, because these sit *above* the address book and every
     * row here is a row of contacts pushed off the screen. Five is about the number of group
     * threads anybody is actually active in; the rest are reachable by typing, which is the
     * same bargain the recents list makes.
     */
    const val RESTING = 5

    /** Newest activity first — the conversation list's order, which is where these were just read from. */
    fun ordered(all: List<Group>): List<Group> = all.sortedByDescending { it.lastDate }

    /**
     * Whether [group] matches what's been typed.
     *
     * Names only, and at the start of any word, matching [Recipients.matches] — a picker where
     * the two halves of one list search by different rules is a picker that appears to be
     * broken.
     *
     * **A query with a digit in it excludes every group.** In the contact half, digits search
     * the address: they are how you find somebody by their number. A group has no address to
     * search — matching them against the guid would make "4" return all of them — so the only
     * honest reading of a digit is that the user is looking for a person, and the GROUPS
     * heading should get out of the way rather than sit above the one contact that matched.
     */
    fun matches(group: Group, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.any { it.isDigit() }) return false
        return wordStartMatch(group.name, trimmed)
    }

    private fun wordStartMatch(name: String, letters: String): Boolean {
        val needle = letters.lowercase()
        if (name.startsWith(needle, ignoreCase = true)) return true
        var atWordStart = false
        for (i in name.indices) {
            val c = name[i]
            if (!c.isLetterOrDigit()) { atWordStart = true; continue }
            if (atWordStart) {
                atWordStart = false
                if (name.startsWith(needle, startIndex = i, ignoreCase = true)) return true
            }
        }
        return false
    }
}
