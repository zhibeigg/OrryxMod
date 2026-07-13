package io.github.orryxmod.feature.bloom

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BloomConfigManagerTest {

    @BeforeEach
    fun setup() { BloomConfigManager.clear() }

    @AfterEach
    fun teardown() { BloomConfigManager.clear() }

    private fun config(name: String, priority: Int = 0) =
        BloomConfig(name, intArrayOf(255, 255, 255, 255), 1f, 32f, priority)

    @Test
    fun `syncAll replaces all configs`() {
        BloomConfigManager.update("old", config("Old"))
        BloomConfigManager.syncAll(mapOf("a" to config("A"), "b" to config("B")))

        assertTrue(BloomConfigManager.hasConfigs())
        assertNull(BloomConfigManager.findConfig("Old"))
    }

    @Test
    fun `update adds or replaces config`() {
        BloomConfigManager.update("id1", config("Zombie"))
        assertNotNull(BloomConfigManager.findConfig("Zombie"))

        BloomConfigManager.update("id1", config("Skeleton"))
        assertNull(BloomConfigManager.findConfig("Zombie"))
        assertNotNull(BloomConfigManager.findConfig("Skeleton"))
    }

    @Test
    fun `remove deletes config`() {
        BloomConfigManager.update("id1", config("Zombie"))
        BloomConfigManager.remove("id1")
        assertNull(BloomConfigManager.findConfig("Zombie"))
    }

    @Test
    fun `clear removes all configs`() {
        BloomConfigManager.update("id1", config("A"))
        BloomConfigManager.update("id2", config("B"))
        BloomConfigManager.clear()
        assertFalse(BloomConfigManager.hasConfigs())
    }

    @Test
    fun `findConfig matches by name case-insensitive`() {
        BloomConfigManager.update("id1", config("zombie"))
        assertNotNull(BloomConfigManager.findConfig("Zombie_King"))
    }

    @Test
    fun `findConfig returns highest priority match`() {
        BloomConfigManager.update("id1", config("mob", priority = 1))
        BloomConfigManager.update("id2", config("mob", priority = 10))
        BloomConfigManager.update("id3", config("mob", priority = 5))

        val result = BloomConfigManager.findConfig("mob_entity")
        assertNotNull(result)
        assertEquals(10, result!!.priority)
    }

    @Test
    fun `findConfig returns null when no match`() {
        BloomConfigManager.update("id1", config("Zombie"))
        assertNull(BloomConfigManager.findConfig("Skeleton"))
    }

    @Test
    fun `hasConfigs returns false when empty`() {
        assertFalse(BloomConfigManager.hasConfigs())
    }

    @Test
    fun `cached misses are invalidated when config changes`() {
        assertNull(BloomConfigManager.findConfig("Zombie King"))

        BloomConfigManager.update("zombie", config("Zombie", priority = 2))

        assertEquals("Zombie", BloomConfigManager.findConfig("Zombie King")?.name)
    }

    @Test
    fun `findMatch preserves candidate name order and stable group id`() {
        BloomConfigManager.update("display", config("Display", priority = 10))
        BloomConfigManager.update("base", config("Base", priority = 1))

        val result = BloomConfigManager.findMatch("Base Entity", "Display Entity")

        assertNotNull(result)
        assertEquals("base", result!!.groupKey)
        assertEquals("Base", result.config.name)
    }

    @Test
    fun `equal priority matches use stable config id ordering`() {
        BloomConfigManager.update("z-last", config("mob", priority = 5))
        BloomConfigManager.update("a-first", config("mob", priority = 5))

        assertEquals("a-first", BloomConfigManager.findMatch("mob")?.groupKey)
    }

    @Test
    fun `BloomConfig equals with IntArray`() {
        val a = BloomConfig("test", intArrayOf(1, 2, 3, 4), 1f, 32f, 0)
        val b = BloomConfig("test", intArrayOf(1, 2, 3, 4), 1f, 32f, 0)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `BloomConfig not equals with different IntArray`() {
        val a = BloomConfig("test", intArrayOf(1, 2, 3, 4), 1f, 32f, 0)
        val b = BloomConfig("test", intArrayOf(5, 6, 7, 8), 1f, 32f, 0)
        assertNotEquals(a, b)
    }
}
