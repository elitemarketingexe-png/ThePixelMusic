package com.unshoo.pixelmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class CacheTrimmingTest {

    private val now = 1_000_000L

    private fun mapOf(vararg expiries: Long): ConcurrentHashMap<String, Long> =
        ConcurrentHashMap<String, Long>().apply { expiries.forEachIndexed { i, e -> put("k$i", e) } }

    @Test
    fun `under or at the cap nothing is touched even if entries are expired`() {
        val m = mapOf(1L, 2L, 3L)
        m.trimTo(3, now) { it }
        assertEquals(3, m.size)
    }

    @Test
    fun `over the cap expired entries are dropped first and live ones kept`() {
        // 2 expired + 3 live, cap 4: removing the expired pair is enough.
        val m = mapOf(10L, 20L, now + 100, now + 200, now + 300)
        m.trimTo(4, now) { it }
        assertEquals(3, m.size)
        assertFalse(m.containsKey("k0"))
        assertFalse(m.containsKey("k1"))
        assertTrue(m.containsKey("k4"))
    }

    @Test
    fun `over the cap with only live entries trims the soonest to expire down to 75 percent`() {
        val m = ConcurrentHashMap<String, Long>()
        for (i in 0 until 101) m["k$i"] = now + 1_000 + i   // k0 expires first
        m.trimTo(100, now) { it }
        assertEquals(75, m.size)
        assertFalse(m.containsKey("k0"))
        assertFalse(m.containsKey("k25"))
        assertTrue(m.containsKey("k26"))   // 101 - 75 = 26 removed: k0..k25
        assertTrue(m.containsKey("k100"))
    }

    @Test
    fun `size stays bounded across many puts`() {
        val m = ConcurrentHashMap<String, Long>()
        for (i in 0 until 5_000) {
            m["k$i"] = now + i
            m.trimTo(256, now) { it }
        }
        assertTrue("size=${m.size}", m.size <= 256)
        assertTrue(m.containsKey("k4999")) // newest survives
    }

    @Test
    fun `entry replaced concurrently is not removed by a stale snapshot`() {
        val m = ConcurrentHashMap<String, Long>()
        for (i in 0 until 10) m["k$i"] = now + i
        // Simulate another thread refreshing k0 after trimTo took its snapshot: remove(key, value)
        // must only remove the exact value it saw.
        val stale = m["k0"]!!
        m["k0"] = now + 99_999
        assertFalse(m.remove("k0", stale))
        assertTrue(m.containsKey("k0"))
    }
}
