package com.gios.lightcamera

import com.gios.lightcamera.send.Group
import com.gios.lightcamera.send.Groups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsTest {

    private fun group(name: String, last: Long = 0L, size: Int = 3) =
        Group(guid = "iMessage;+;chat$name", name = name, size = size, lastDate = last)

    @Test
    fun `ordered puts the newest activity first`() {
        val out = Groups.ordered(
            listOf(group("Old", last = 100), group("New", last = 900), group("Middle", last = 500)),
        )
        assertEquals(listOf("New", "Middle", "Old"), out.map { it.name })
    }

    @Test
    fun `a blank query keeps everything`() {
        assertTrue(Groups.matches(group("Sunday Roast"), ""))
        assertTrue(Groups.matches(group("Sunday Roast"), "   "))
    }

    @Test
    fun `matching is on word starts, like the contact list`() {
        val g = group("Sunday Roast Crew")
        assertTrue(Groups.matches(g, "sun"))
        assertTrue(Groups.matches(g, "roast"))
        assertTrue(Groups.matches(g, "crew"))
        // Infix, which the contact search deliberately refuses — "oast" is inside "Roast" but
        // starts no word in it, and allowing it returns half the list for a two-letter query.
        assertFalse(Groups.matches(g, "oast"))
    }

    @Test
    fun `punctuation starts a word`() {
        assertTrue(Groups.matches(group("Mary-Jane's Birthday"), "jane"))
        assertTrue(Groups.matches(group("Mary-Jane's Birthday"), "birth"))
    }

    @Test
    fun `a numeric query excludes every group`() {
        // Digits mean the user is looking for a phone number, which is a person. If groups
        // survived it, the GROUPS heading would sit above the one contact that actually matched.
        assertFalse(Groups.matches(group("Sunday Roast"), "555"))
        assertFalse(Groups.matches(group("Flat 4"), "4"))
    }

    @Test
    fun `the subtitle counts people`() {
        assertEquals("4 people", group("Anything", size = 4).subtitle)
        // Nothing useful to say, so it says what it is rather than "0 people".
        assertEquals("Group", group("Anything", size = 0).subtitle)
        assertEquals("Group", group("Anything", size = 1).subtitle)
    }
}
