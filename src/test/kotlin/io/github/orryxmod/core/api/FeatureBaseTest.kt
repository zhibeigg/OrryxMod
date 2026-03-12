package io.github.orryxmod.core.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FeatureBaseTest {

    @Feature(id = "test-feature", description = "A test feature")
    class TestFeature : FeatureBase()

    class NoAnnotationFeature : FeatureBase()

    @Test
    fun `enable and disable state toggle`() {
        val feature = TestFeature()
        assertTrue(feature.enabled)

        feature.disable()
        assertFalse(feature.enabled)

        feature.enable()
        assertTrue(feature.enabled)
    }

    @Test
    fun `metadata parsed from Feature annotation`() {
        val feature = TestFeature()
        assertEquals("test-feature", feature.metadata.id)
        assertEquals("A test feature", feature.metadata.description)
    }

    @Test
    fun `missing Feature annotation throws exception`() {
        val feature = NoAnnotationFeature()
        assertThrows<IllegalStateException> {
            feature.metadata
        }
    }
}
