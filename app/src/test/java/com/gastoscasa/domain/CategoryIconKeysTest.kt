package com.gastoscasa.domain

import com.gastoscasa.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CategoryIconKeysTest {
    @Test
    fun builtInKeysVsLegacyEmoji() {
        assertTrue(CategoryIconKeys.isBuiltIn("ic:food"))
        assertTrue(CategoryIconKeys.isBuiltIn("ic:swap"))
        assertFalse(CategoryIconKeys.isBuiltIn("🍽️"))
        assertFalse(CategoryIconKeys.isBuiltIn("ic:unknown"))
        assertEquals("ic:party", CategoryIconKeys.normalize("ic:party"))
        assertEquals("🎉", CategoryIconKeys.normalize(" 🎉 "))
        assertEquals("ic:tag", CategoryIconKeys.normalize("  "))
        assertEquals(12, CategoryIconKeys.selectable.size)
    }

    @Test
    fun relativeTime() {
        val now = Instant.parse("2026-11-24T12:00:00Z")
        assertEquals("ahora", Fmt.ago(now, now))
        assertEquals("hace 5 min", Fmt.ago(now.minusSeconds(300), now))
        assertEquals("hace 3 h", Fmt.ago(now.minusSeconds(3 * 3600), now))
        assertEquals("hace 2 d", Fmt.ago(now.minusSeconds(2 * 86400), now))
    }
}
