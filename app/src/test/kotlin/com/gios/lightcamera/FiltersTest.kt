package com.gios.lightcamera

import com.gios.lightcamera.filter.Filters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What can be checked about a shader without a GPU.
 *
 * Not much, but the things that can be are exactly the things that fail silently on device:
 * AGSL that doesn't compile leaves the viewfinder unfiltered with only a log line to say so,
 * so a missing entry point or an unbalanced brace would ship unnoticed. The real compile
 * happens in [com.gios.lightcamera.filter.ShaderRuntime] at runtime.
 */
class FiltersTest {

    @Test
    fun `ids are unique`() {
        val ids = Filters.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `only None has no shader`() {
        val without = Filters.all.filter { it.agsl == null }
        assertEquals(listOf("none"), without.map { it.id })
        assertNull(Filters.none.source)
    }

    @Test
    fun `every shader declares the entry point AGSL expects`() {
        Filters.all.mapNotNull { it.agsl?.let { source -> it.id to source } }
            .forEach { (id, source) ->
                assertTrue("$id has no main()", source.contains("half4 main(float2 "))
            }
    }

    @Test
    fun `every shader gets the prelude exactly once`() {
        Filters.all.filter { it.agsl != null }.forEach { filter ->
            val source = filter.source!!
            assertEquals(
                "${filter.id} declares src ${source.split("uniform shader src").size - 1} times",
                1,
                source.split("uniform shader src").size - 1,
            )
            assertTrue("${filter.id} is missing the helpers", source.contains("float lum(float3"))
        }
    }

    @Test
    fun `braces and parentheses balance`() {
        Filters.all.filter { it.agsl != null }.forEach { filter ->
            val source = filter.source!!
            assertEquals(
                "${filter.id} has unbalanced braces",
                source.count { it == '{' },
                source.count { it == '}' },
            )
            assertEquals(
                "${filter.id} has unbalanced parens",
                source.count { it == '(' },
                source.count { it == ')' },
            )
        }
    }

    @Test
    fun `shaders only read uniforms the runtime sets`() {
        // ShaderRuntime sets size and seed and binds src. A shader that declared a fourth
        // uniform would compile and then sample garbage.
        Filters.all.filter { it.agsl != null }.forEach { filter ->
            val declared = Regex("uniform\\s+\\w+\\s+(\\w+)\\s*;")
                .findAll(filter.source!!)
                .map { it.groupValues[1] }
                .toSet()
            assertEquals(setOf("src", "size", "seed"), declared)
        }
    }

    @Test
    fun `labels fit the top bar`() {
        Filters.all.forEach { filter ->
            assertTrue(
                "${filter.id} label is too long for the viewfinder",
                filter.label.length <= 9,
            )
        }
    }

    @Test
    fun `only the filters that use the seed are animated`() {
        Filters.all.filter { it.animated }.forEach { filter ->
            assertTrue(
                "${filter.id} animates but never reads seed",
                filter.agsl!!.contains("hash("),
            )
        }
    }

    @Test
    fun `stepping wraps in both directions`() {
        val first = Filters.all.first()
        val last = Filters.all.last()
        assertEquals(last.id, Filters.step(first, -1).id)
        assertEquals(first.id, Filters.step(last, 1).id)
        assertEquals(Filters.all[1].id, Filters.step(first, 1).id)
    }

    @Test
    fun `an unknown id falls back to None rather than crashing`() {
        assertEquals(Filters.none.id, Filters.byId("nope").id)
        assertEquals(Filters.none.id, Filters.byId(null).id)
    }
}
