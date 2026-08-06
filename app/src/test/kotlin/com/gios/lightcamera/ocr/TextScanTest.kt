package com.gios.lightcamera.ocr

import com.gios.lightcamera.qr.Codes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extraction from recognised text, which is the half of this feature that can be wrong quietly.
 *
 * The recogniser either reads the characters or it does not, and that needs a phone and a page.
 * What happens to the characters afterwards has exactly one right answer per input, so it is
 * tested here instead.
 */
class TextScanTest {

    private fun payloads(text: String) = TextScan.found(text).map { it.payload }

    @Test
    fun `an address is claimed once, not three times`() {
        // The bug the blanking passes exist for: an email contains a bare domain, so a naive
        // implementation reports ada@example.com, https://example.com and nothing useful.
        val found = TextScan.found("write to ada@example.com about it")
        assertEquals(1, found.size)
        assertEquals("mailto:ada@example.com", found.first().payload)
        assertEquals(Codes.Kind.Email, found.first().kind)
    }

    @Test
    fun `a bare host becomes a link, a version number does not`() {
        assertEquals(listOf("https://example.com"), payloads("see example.com for more"))
        // Codes.isBareDomain does the rejecting. LightQR shipped the opposite behaviour and
        // offered to open https://1.2 from a code containing a version number.
        assertTrue(payloads("running version 1.2 today").isEmpty())
    }

    @Test
    fun `a full url keeps its path and loses the sentence's full stop`() {
        assertEquals(
            listOf("https://example.com/a/b?x=1"),
            payloads("go to https://example.com/a/b?x=1."),
        )
    }

    @Test
    fun `a phone number becomes a tel, punctuation and all`() {
        val found = TextScan.found("call (555) 013-4567 after six")
        assertEquals(1, found.size)
        assertEquals("tel:5550134567", found.first().payload)
        // The label is what the page said, not what we made of it — that is what you check
        // against the card in your other hand.
        assertEquals("(555) 013-4567", found.first().label)
    }

    @Test
    fun `an international number keeps its plus`() {
        assertEquals(listOf("tel:+33142868200"), payloads("Paris +33 1 42 86 82 00"))
    }

    @Test
    fun `digit runs that are not phone numbers are left alone`() {
        // Too short to be a subscriber number, and too long to be one.
        assertTrue(payloads("order 12-3456").isEmpty())
        assertTrue(payloads("card 1234 5678 9012 3456 7890 1234").isEmpty())
    }

    @Test
    fun `a year range is not a phone number`() {
        // Eight digits in two groups, exactly like a real local number, so length cannot tell
        // them apart. Found by this test rather than by someone ringing a copyright line.
        assertTrue(payloads("open 2019-2024").isEmpty())
        assertTrue(payloads("\u00a9 1998 \u2013 2026").isEmpty())
        // The guard must stay narrow: a real number that happens to start 2019 is still a number.
        assertEquals(listOf("tel:20193456789"), payloads("ring 2019 345 6789"))
    }

    @Test
    fun `a number wrapped in brackets keeps neither`() {
        // The closing bracket is trimmed as sentence punctuation, so the opening one has to go
        // too or the label reads like a bug in this code rather than in the page.
        assertEquals("5550134567", TextScan.found("ring (5550134567)").first().label)
        // ...but a dialling code in brackets keeps both, because the pair is still intact.
        assertEquals("(555) 013-4567", TextScan.found("ring (555) 013-4567").first().label)
    }

    @Test
    fun `duplicates collapse but order is the page's`() {
        val text = "ada@example.com\ncall 555 013 4567\nada@example.com"
        assertEquals(listOf("mailto:ada@example.com", "tel:5550134567"), payloads(text))
    }

    @Test
    fun `a page with nothing actionable yields nothing`() {
        assertTrue(TextScan.found("MILK\nBREAD\nTOTAL 4.20").isEmpty())
    }

    @Test
    fun `line breaks survive, blank runs collapse`() {
        // A receipt's line breaks are its only structure. Re-flowing into prose would destroy
        // the thing that makes the copy worth having.
        assertEquals("MILK\nBREAD\n\nTOTAL", TextScan.page("MILK  \nBREAD\n\n\n\nTOTAL\n\n"))
    }

    @Test
    fun `the heading names one kind, or says there are several`() {
        assertEquals("TEXT", TextScan.heading(emptyList()))
        assertEquals(Codes.heading(Codes.Kind.Email), TextScan.heading(TextScan.found("a@b.com")))
        assertEquals("ON THIS PAGE", TextScan.heading(TextScan.found("a@b.com and 555 013 4567")))
    }

    @Test
    fun `everything found is openable, since that is the sheet's promise`() {
        // The sheet only shows OPEN when Codes.openable answers. Anything extracted here should
        // survive that round trip, or it was extracted into a dead end.
        val text = "ada@example.com  example.com  +33 1 42 86 82 00  https://x.dev/p"
        TextScan.found(text).forEach {
            assertTrue("${it.payload} is not openable", Codes.openable(it.payload) != null)
        }
    }
}
