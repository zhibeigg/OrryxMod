package io.github.orryxmod.core.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenderUtilsTest {

    @Test
    fun `GL float query buffer satisfies LWJGL fixed capacity requirement`() {
        val buffer = RenderUtils.createGlFloatQueryBuffer()

        assertEquals(16, RenderUtils.GL_FLOAT_QUERY_CAPACITY)
        assertEquals(16, buffer.capacity())
        assertEquals(16, buffer.remaining())
    }

    @Test
    fun `NIO buffer state helpers retain Java 8 compatible behavior`() {
        val buffer = RenderUtils.createGlFloatQueryBuffer()
        buffer.put(1f)

        RenderUtils.flipBufferForJava8(buffer)
        assertEquals(0, buffer.position())
        assertEquals(1, buffer.limit())

        RenderUtils.clearBufferForJava8(buffer)
        assertEquals(0, buffer.position())
        assertEquals(buffer.capacity(), buffer.limit())
    }
}
