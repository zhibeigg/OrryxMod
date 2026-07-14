package io.github.orryxmod.util

import baritone.api.Settings
import net.minecraft.init.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color

class BaritoneUtilsTest {

    @Test
    fun `defaults can be applied before BaritoneAPI static initialization completes`() {
        Bootstrap.register()
        val constructor = Settings::class.java.getDeclaredConstructor().apply { isAccessible = true }
        val settings = constructor.newInstance()

        BaritoneUtils.applyDefaults(settings)

        assertFalse(BaritoneUtils.initialized)
        assertFalse(settings.chatControl.value)
        assertFalse(settings.chatControlAnyway.value)
        assertFalse(settings.allowBreak.value)
        assertTrue(settings.allowSprint.value)
        assertEquals(Color(44, 255, 46), settings.colorCurrentPath.value)
        assertEquals(3_000L, settings.failureTimeoutMS.value)
        assertFalse(settings.disconnectOnArrival.value)
    }
}
