package com.gios.lightcamera.ocr

import com.gios.lightcamera.qr.Codes

/**
 * One thing worth acting on, lifted out of a photographed page.
 *
 * [payload] is deliberately shaped like a QR code's contents rather than like the characters on
 * the page: a photographed phone number becomes `tel:+15551234567`, an address becomes
 * `mailto:…`, a bare host becomes `https://…`. That is the whole trick of this feature — once a
 * scrap of text is in that shape, every judgement about it is already written and tested in
 * [Codes], and every action on it is already written in `CodeHandoff`. A number photographed off
 * a business card ends up in exactly the same sheet as one inside a QR code, because after this
 * line of code they are the same thing.
 *
 * [label] is what the page actually said, kept verbatim so the sheet can show the reading and
 * the interpretation side by side. They differ more often than you would like.
 */
data class Found(val label: String, val payload: String, val kind: Codes.Kind)

/**
 * Turning a page of recognised text into a short list of things you might want.
 *
 * **No Android imports anywhere in this file, deliberately** — the same rule as [Codes], for the
 * same reason. All of this is string work with one right answer per input, so it is tested on the
 * JVM instead of by pointing a phone at a business card.
 *
 * The difference from QR is worth stating, because it is why this file exists at all. A QR
 * payload *is* the thing: the whole string is the address. Recognised text is prose with things
 * embedded in it — "call me on 555 0134" — so the work here is extraction first and
 * classification second. [Codes.kindOf] is then handed something it already understands.
 *
 * **Nothing is spell-corrected, and that is a decision rather than an omission.** Recognisers
 * confuse `O` with `0` and `l` with `1`, and those two substitutions are most of the difference
 * between a company's website and a domain someone bought to catch the typo. Guessing would make
 * this feature quietly dangerous in exactly the case it looks most useful — a printed URL. So a
 * misread is shown as it was read, and the person decides.
 */
object TextScan {

    /**
     * Everything found, in the order it appeared on the page.
     *
     * Reading order rather than by kind: on a business card the name is at the top and the phone
     * number at the bottom, and shuffling them into categories throws away the only layout
     * information the recogniser gave us.
     */
    fun found(text: String): List<Found> {
        val out = LinkedHashMap<String, Found>()
        // Passes run most-specific first, and each one blanks the characters it claimed before
        // the next sees the page. An email address contains a bare domain; a URL contains digits
        // that punctuate like a phone number. Without the blanking, `ada@example.com` would be
        // reported three times — as an address, as `https://example.com`, and as nothing useful.
        var rest = text

        out += matches(rest, EMAIL) { Found(it, "mailto:$it", Codes.Kind.Email) }
        rest = blank(rest, EMAIL)

        out += matches(rest, URL) { Found(it, it, Codes.Kind.Link) }
        rest = blank(rest, URL)

        out += matches(rest, BARE_HOST) {
            // Codes owns what counts as a host, so a version number or a filename is rejected by
            // the same rule that rejects it in a QR payload rather than by a second opinion here.
            if (Codes.isBareDomain(it)) Found(it, "https://$it", Codes.Kind.Link) else null
        }
        rest = blank(rest, BARE_HOST)

        out += matches(rest, PHONE) { match ->
            val label = balance(match)
            val digits = label.count(Char::isDigit)
            when {
                // Seven is the shortest real subscriber number; sixteen is past the E.164
                // ceiling, so anything longer is an account number, an ISBN or a reference.
                digits !in 7..16 -> null
                YEAR_RANGE.matches(label) -> null
                else -> Found(label, "tel:" + label.filter { it.isDigit() || it == '+' }, Codes.Kind.Phone)
            }
        }

        return out.values.toList()
    }

    /**
     * The page as text, with the recogniser's line breaks left alone.
     *
     * Not re-flowed into paragraphs. A recogniser breaks lines where the *page* breaks them, so
     * on a receipt or a form those breaks are the only structure there is, and joining them into
     * prose would destroy the thing that makes the copy useful. Blank runs are collapsed and
     * trailing spaces are dropped, which is tidying rather than interpretation.
     */
    fun page(text: String): String = text
        .lineSequence()
        .map { it.trimEnd() }
        .fold(mutableListOf<String>()) { acc, line ->
            if (line.isNotEmpty() || acc.lastOrNull()?.isNotEmpty() == true) acc.add(line)
            acc
        }
        .joinToString("\n")
        .trim()

    /** The word at the top of the sheet, when there is more than one kind of thing on the page. */
    fun heading(found: List<Found>): String = when {
        found.isEmpty() -> "TEXT"
        found.map { it.kind }.distinct().size == 1 -> Codes.heading(found.first().kind)
        else -> "ON THIS PAGE"
    }

    /** Every match of [pattern], cleaned and turned into a [Found], keyed by payload. */
    private inline fun matches(
        text: String,
        pattern: Regex,
        make: (String) -> Found?,
    ): Map<String, Found> {
        val out = LinkedHashMap<String, Found>()
        for (match in pattern.findAll(text)) {
            // Trailing punctuation belongs to the sentence, not to the address. A URL at the end
            // of a line reads as `example.com.` and would otherwise become a host with a dot.
            val cleaned = match.value.trim().trimEnd('.', ',', ';', ':', ')', ']', '>', '"', '\'')
            if (cleaned.isEmpty()) continue
            make(cleaned)?.let { out.putIfAbsent(it.payload, it) }
        }
        return out
    }

    /**
     * Drop a leading bracket that lost its partner to [matches]'s trailing-punctuation trim.
     *
     * `(555) 013-4567` keeps both brackets, because the closing one is in the middle. A number
     * written entirely inside brackets does not, and `(5550134567` as a label reads like a typo
     * in our code rather than in the page.
     */
    private fun balance(text: String): String =
        if (text.startsWith("(") && !text.contains(")")) text.removePrefix("(").trim() else text

    /** Replace matches with spaces so a later, looser pattern cannot re-claim the same characters. */
    private fun blank(text: String, pattern: Regex): String =
        pattern.replace(text) { " ".repeat(it.value.length) }

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    private val URL = Regex("""\bhttps?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    private val BARE_HOST = Regex("""\b(?:[A-Za-z0-9](?:[A-Za-z0-9\-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}(?:/[^\s<>"']*)?""")

    /**
     * Punctuated digit runs, checked for length afterwards rather than in the pattern.
     *
     * Written loosely on purpose: international formats vary more than any single expression can
     * usefully capture, and a pattern strict enough to reject a date is also strict enough to
     * reject a French number. The digit count in [found] does the rejecting, where it can be read.
     */
    private val PHONE = Regex("""\+?\(?\d[\d\s().\-]{5,20}\d""")

    /**
     * A span of years, which punctuates exactly like a local phone number and is not one.
     *
     * `2019-2024` is eight digits in two groups, and so is a perfectly real subscriber number,
     * so length cannot separate them. Narrow on purpose: this rejects the one collision that
     * actually turns up on printed pages — opening hours, a copyright line, a CV — rather than
     * guessing at numbers in general.
     */
    private val YEAR_RANGE = Regex("""(19|20)\d\d\s*[-\u2013]\s*(19|20)\d\d""")
}
