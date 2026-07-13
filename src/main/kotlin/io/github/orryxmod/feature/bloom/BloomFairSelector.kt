package io.github.orryxmod.feature.bloom

/**
 * 在配置组之间做带权轮转：高优先级组拥有更多轮转槽位，但所有非空组都会被选中。
 * 状态只由客户端渲染线程访问。
 */
internal class BloomFairSelector {
    private var groupCursor = 0L

    fun <T> select(
        candidates: List<T>,
        maxTotal: Int,
        maxGroups: Int,
        groupKey: (T) -> String,
        priorityWeight: (T) -> Int,
        distanceSq: (T) -> Double
    ): List<T> {
        if (candidates.isEmpty()) return emptyList()

        val entityLimit = if (maxTotal <= 0) candidates.size else minOf(maxTotal, candidates.size)
        if (entityLimit <= 0) return emptyList()

        val groups = candidates
            .groupBy(groupKey)
            .map { (key, groupCandidates) ->
                CandidateGroup(
                    key = key,
                    weight = priorityWeight(groupCandidates.first()).coerceIn(1, MAX_PRIORITY_WEIGHT),
                    candidates = groupCandidates.sortedBy(distanceSq)
                )
            }
            .sortedWith(compareByDescending<CandidateGroup<T>> { it.weight }.thenBy { it.key })

        val configuredGroupLimit = if (maxGroups <= 0) groups.size else minOf(maxGroups, groups.size)
        val groupLimit = minOf(configuredGroupLimit, entityLimit)
        val selectedGroups = selectGroups(groups, groupLimit)
            .sortedWith(compareByDescending<CandidateGroup<T>> { it.weight }.thenBy { it.key })
        if (selectedGroups.isEmpty()) return emptyList()

        val selected = ArrayList<T>(entityLimit)
        val nextIndexes = HashMap<String, Int>(selectedGroups.size)

        // 每个本帧入选组先获得一个保底名额，避免高优先级组吞掉全部实体配额。
        selectedGroups.forEach { group ->
            selected.add(group.candidates.first())
            nextIndexes[group.key] = 1
        }

        // 剩余名额仍按优先级顺序轮询，保留高优先级配置的吞吐优势。
        while (selected.size < entityLimit) {
            var progressed = false
            for (group in selectedGroups) {
                if (selected.size >= entityLimit) break
                val nextIndex = nextIndexes[group.key] ?: 0
                if (nextIndex >= group.candidates.size) continue
                selected.add(group.candidates[nextIndex])
                nextIndexes[group.key] = nextIndex + 1
                progressed = true
            }
            if (!progressed) break
        }

        return selected
    }

    fun reset() {
        groupCursor = 0L
    }

    private fun <T> selectGroups(groups: List<CandidateGroup<T>>, limit: Int): List<CandidateGroup<T>> {
        if (limit >= groups.size) return groups

        val scheduleSize = groups.sumOf { it.weight }
        if (scheduleSize <= 0) return groups.take(limit)

        val selected = LinkedHashMap<String, CandidateGroup<T>>(limit)
        var visitedSlots = 0
        while (selected.size < limit && visitedSlots < scheduleSize) {
            val slot = floorMod(groupCursor, scheduleSize.toLong()).toInt()
            groupCursor++
            visitedSlots++

            var offset = 0
            for (group in groups) {
                offset += group.weight
                if (slot < offset) {
                    selected.putIfAbsent(group.key, group)
                    break
                }
            }
        }

        return selected.values.toList()
    }

    private fun floorMod(value: Long, divisor: Long): Long {
        val remainder = value % divisor
        return if (remainder >= 0L) remainder else remainder + divisor
    }

    private data class CandidateGroup<T>(
        val key: String,
        val weight: Int,
        val candidates: List<T>
    )

    private companion object {
        const val MAX_PRIORITY_WEIGHT = 8
    }
}
