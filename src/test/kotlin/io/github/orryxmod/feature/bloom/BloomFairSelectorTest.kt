package io.github.orryxmod.feature.bloom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BloomFairSelectorTest {
    private data class Candidate(
        val id: String,
        val group: String,
        val weight: Int,
        val distance: Double
    )

    private fun BloomFairSelector.select(candidates: List<Candidate>, maxTotal: Int, maxGroups: Int) =
        select(
            candidates = candidates,
            maxTotal = maxTotal,
            maxGroups = maxGroups,
            groupKey = { it.group },
            priorityWeight = { it.weight },
            distanceSq = { it.distance }
        )

    @Test
    fun `weighted rotation never starves lower priority group`() {
        val selector = BloomFairSelector()
        val candidates = listOf(
            Candidate("high", "high", 2, 1.0),
            Candidate("low", "low", 1, 1.0)
        )

        val selectedGroups = (0 until 9).map {
            selector.select(candidates, maxTotal = 1, maxGroups = 1).single().group
        }

        assertTrue("low" in selectedGroups)
        assertTrue(selectedGroups.count { it == "high" } > selectedGroups.count { it == "low" })
    }

    @Test
    fun `group cap rotates access across frames`() {
        val selector = BloomFairSelector()
        val candidates = listOf(
            Candidate("high", "high", 3, 1.0),
            Candidate("medium", "medium", 2, 1.0),
            Candidate("low", "low", 1, 1.0)
        )

        val seenGroups = LinkedHashSet<String>()
        repeat(4) {
            selector.select(candidates, maxTotal = 2, maxGroups = 2)
                .forEach { seenGroups.add(it.group) }
        }

        assertEquals(setOf("high", "medium", "low"), seenGroups)
    }

    @Test
    fun `selected groups receive base quota before priority surplus`() {
        val selector = BloomFairSelector()
        val candidates = listOf(
            Candidate("h1", "high", 2, 1.0),
            Candidate("h2", "high", 2, 2.0),
            Candidate("h3", "high", 2, 3.0),
            Candidate("l1", "low", 1, 1.0),
            Candidate("l2", "low", 1, 2.0),
            Candidate("l3", "low", 1, 3.0)
        )

        val selected = selector.select(candidates, maxTotal = 5, maxGroups = 2)

        assertEquals(5, selected.size)
        assertTrue(selected.any { it.group == "low" })
        assertEquals(listOf("h1", "h2", "h3"), selected.filter { it.group == "high" }.map { it.id })
    }
}
