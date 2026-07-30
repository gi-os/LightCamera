package com.gios.lightcamera

import com.gios.lightcamera.send.Address
import com.gios.lightcamera.send.Recipient
import com.gios.lightcamera.send.Recipients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things in the send picker that are easy to get subtly wrong: deciding two spellings
 * of a phone number are the same person, and deciding what a typed query matches.
 */
class RecipientsTest {

    private fun phone(id: Long, name: String, number: String, label: String = "Mobile") =
        Recipients.Row(id, name, number, Address.Kind.Phone, label)

    private fun email(id: Long, name: String, address: String) =
        Recipients.Row(id, name, address, Address.Kind.Email, "Home")

    /* ---------------- keys ---------------- */

    @Test
    fun `the same number written five ways has one key`() {
        val forms = listOf(
            "+1 (212) 555-0148",
            "212-555-0148",
            "212.555.0148",
            "2125550148",
            "+12125550148",
        )
        val keys = forms.map { Recipients.key(it, Address.Kind.Phone) }.distinct()
        assertEquals(listOf("2125550148"), keys)
    }

    @Test
    fun `a short number keeps all of its digits`() {
        // A shortcode has fewer digits than the match window; truncating from the right would
        // turn every one of them into the empty string.
        assertEquals("611", Recipients.key("611", Address.Kind.Phone))
    }

    @Test
    fun `two genuinely different numbers keep different keys`() {
        val a = Recipients.key("+1 212 555 0148", Address.Kind.Phone)
        val b = Recipients.key("+1 212 555 0149", Address.Kind.Phone)
        assertTrue(a != b)
    }

    @Test
    fun `email keys fold case but not dots`() {
        assertEquals("alex@example.com", Recipients.key("  Alex@Example.COM ", Address.Kind.Email))
        // Dot-stripping is a Gmail-only rule; applying it generally would merge two real addresses.
        assertTrue(
            Recipients.key("a.b@example.com", Address.Kind.Email) !=
                Recipients.key("ab@example.com", Address.Kind.Email),
        )
    }

    /* ---------------- merge ---------------- */

    @Test
    fun `rows collapse into people, phones before emails`() {
        val merged = Recipients.merge(
            listOf(
                email(1, "Alex", "alex@example.com"),
                phone(1, "Alex", "212-555-0148"),
                phone(2, "Basil", "212-555-0100"),
            ),
        )
        assertEquals(listOf("Alex", "Basil"), merged.map { it.name })
        val alex = merged.first { it.name == "Alex" }
        assertEquals(2, alex.addresses.size)
        assertEquals(Address.Kind.Phone, alex.primary?.kind)
    }

    @Test
    fun `one person's duplicate number appears once`() {
        val merged = Recipients.merge(
            listOf(
                phone(1, "Alex", "+1 (212) 555-0148", "Mobile"),
                phone(1, "Alex", "2125550148", "Work"),
            ),
        )
        assertEquals(1, merged.single().addresses.size)
    }

    @Test
    fun `a nameless contact is titled by its address rather than dropped`() {
        val merged = Recipients.merge(listOf(phone(7, "", "212-555-0148")))
        assertEquals("212-555-0148", merged.single().name)
    }

    @Test
    fun `a row with a blank address contributes nothing`() {
        assertTrue(Recipients.merge(listOf(phone(1, "Alex", "   "))).isEmpty())
    }

    @Test
    fun `sorting ignores case`() {
        val merged = Recipients.merge(
            listOf(phone(1, "Zoe", "1"), phone(2, "alex", "2"), phone(3, "Basil", "3")),
        )
        assertEquals(listOf("alex", "Basil", "Zoe"), merged.map { it.name })
    }

    /* ---------------- search ---------------- */

    private val alex = Recipient(
        1,
        "Alex Moreau",
        listOf(Address("+1 (212) 555-0148", "2125550148", Address.Kind.Phone, "Mobile")),
    )

    @Test
    fun `an empty query matches everybody`() {
        assertTrue(Recipients.matches(alex, "   "))
    }

    @Test
    fun `letters match the start of any word in the name`() {
        assertTrue(Recipients.matches(alex, "al"))
        assertTrue(Recipients.matches(alex, "mor"))
        assertTrue(Recipients.matches(alex, "ALEX MOR"))
    }

    @Test
    fun `letters do not match the middle of a word`() {
        // An infix match returns half an address book for a two-letter query.
        assertFalse(Recipients.matches(alex, "ore"))
        assertFalse(Recipients.matches(alex, "lex"))
    }

    @Test
    fun `digits match the number regardless of its punctuation`() {
        assertTrue(Recipients.matches(alex, "5550148"))
        assertTrue(Recipients.matches(alex, "212 555"))
        assertFalse(Recipients.matches(alex, "5550149"))
    }

    @Test
    fun `a query with both halves has to satisfy both`() {
        assertTrue(Recipients.matches(alex, "alex 555"))
        assertFalse(Recipients.matches(alex, "basil 555"))
        assertFalse(Recipients.matches(alex, "alex 999"))
    }

    @Test
    fun `an email address matches by prefix`() {
        val byEmail = Recipient(
            2,
            "B",
            listOf(Address("basil@example.com", "basil@example.com", Address.Kind.Email)),
        )
        assertTrue(Recipients.matches(byEmail, "basil@"))
    }

    /* ---------------- recents ---------------- */

    @Test
    fun `recents come out in the stored order, then everybody else once`() {
        val a = Recipient(1, "Alex", listOf(Address("1", "k-alex", Address.Kind.Phone)))
        val b = Recipient(2, "Basil", listOf(Address("2", "k-basil", Address.Kind.Phone)))
        val c = Recipient(3, "Cleo", listOf(Address("3", "k-cleo", Address.Kind.Phone)))
        val ordered = Recipients.ordered(listOf(a, b, c), listOf("k-cleo", "k-alex"))
        assertEquals(listOf("Cleo", "Alex"), ordered.recent.map { it.name })
        assertEquals(listOf("Basil"), ordered.rest.map { it.name })
    }

    @Test
    fun `a recent whose contact is gone is skipped`() {
        val a = Recipient(1, "Alex", listOf(Address("1", "k-alex", Address.Kind.Phone)))
        val ordered = Recipients.ordered(listOf(a), listOf("k-deleted", "k-alex"))
        assertEquals(listOf("Alex"), ordered.recent.map { it.name })
        assertTrue(ordered.rest.isEmpty())
    }

    @Test
    fun `a person reachable two ways only appears in recents once`() {
        val two = Recipient(
            1,
            "Alex",
            listOf(
                Address("1", "k-phone", Address.Kind.Phone),
                Address("a@b.c", "k-mail", Address.Kind.Email),
            ),
        )
        val ordered = Recipients.ordered(listOf(two), listOf("k-mail", "k-phone"))
        assertEquals(1, ordered.recent.size)
        assertTrue(ordered.rest.isEmpty())
    }

    @Test
    fun `remembering moves an existing entry to the front without duplicating it`() {
        val after = Recipients.remember(listOf("a", "b", "c"), "c")
        assertEquals(listOf("c", "a", "b"), after)
    }

    @Test
    fun `recents are capped`() {
        var list = emptyList<String>()
        repeat(20) { list = Recipients.remember(list, "k$it", limit = 6) }
        assertEquals(6, list.size)
        assertEquals("k19", list.first())
    }

    @Test
    fun `remembering a blank key changes nothing`() {
        assertEquals(listOf("a"), Recipients.remember(listOf("a"), "  ".trim()))
    }

    @Test
    fun `no recents means everybody is in the main list`() {
        val a = Recipient(1, "Alex", listOf(Address("1", "k", Address.Kind.Phone)))
        val ordered = Recipients.ordered(listOf(a), emptyList())
        assertTrue(ordered.recent.isEmpty())
        assertEquals(1, ordered.rest.size)
    }
}
