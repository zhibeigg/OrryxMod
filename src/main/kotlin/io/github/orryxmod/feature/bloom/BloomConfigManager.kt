package io.github.orryxmod.feature.bloom

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 一次匹配的不可变结果。groupKey 使用配置 ID，避免同名配置被错误合并。
 */
internal data class BloomConfigMatch(
    val groupKey: String,
    val config: BloomConfig,
    val priorityWeight: Int
)

/**
 * Bloom 配置管理器。
 *
 * 网络线程只会发布新的不可变匹配索引；渲染线程读取快照，不访问可变配置集合。
 */
object BloomConfigManager {
    @Volatile
    var maxMatchCacheEntries: Int = 2048

    private data class CompiledConfig(
        val match: BloomConfigMatch,
        val normalizedName: String
    )

    private data class MatchingIndex(
        val revision: Long,
        val entries: List<CompiledConfig>,
        val groupsByPriority: Map<Int, List<BloomConfigMatch>>
    ) {
        companion object {
            val EMPTY = MatchingIndex(0L, emptyList(), emptyMap())
        }
    }

    private data class CachedLookup(
        val revision: Long,
        val match: BloomConfigMatch?
    )

    private val updateLock = Any()
    private val configs = LinkedHashMap<String, BloomConfig>()
    private val matchCache = ConcurrentHashMap<String, CachedLookup>()

    @Volatile
    private var matchingIndex = MatchingIndex.EMPTY

    /**
     * 为单个名称查找匹配配置（按优先级排序后取最高优先级）。
     */
    fun findConfig(entityName: String): BloomConfig? = findMatch(entityName)?.config

    /**
     * 按调用方提供的名称顺序匹配；每个名称只做一次规范化并复用匹配缓存。
     */
    internal fun findMatch(vararg entityNames: String?): BloomConfigMatch? {
        val index = matchingIndex
        if (index.entries.isEmpty()) return null

        val seenNames = HashSet<String>(entityNames.size)
        for (entityName in entityNames) {
            val normalizedName = normalize(entityName)
            if (normalizedName.isEmpty() || !seenNames.add(normalizedName)) continue

            val cached = matchCache[normalizedName]
            if (cached != null && cached.revision == index.revision) {
                if (cached.match != null) return cached.match
                continue
            }

            val match = index.entries.firstOrNull { normalizedName.contains(it.normalizedName) }?.match
            synchronized(updateLock) {
                // 容量检查、revision 复检和写入必须原子，避免并发突破硬上限或写回旧索引。
                if (matchingIndex.revision == index.revision) {
                    val cacheLimit = maxMatchCacheEntries.coerceIn(64, 8192)
                    if (matchCache.size >= cacheLimit) matchCache.clear()
                    matchCache[normalizedName] = CachedLookup(index.revision, match)
                }
            }
            if (match != null) return match
        }
        return null
    }

    /** 当前不可变匹配索引的版本，供实体候选缓存安全失效。 */
    internal fun revision(): Long = matchingIndex.revision

    /**
     * 全量同步配置。
     */
    fun syncAll(newConfigs: Map<String, BloomConfig>) {
        synchronized(updateLock) {
            configs.clear()
            newConfigs.forEach { (id, config) ->
                if (id.isNotBlank() && config.name.isNotBlank()) {
                    configs[id] = config
                }
            }
            rebuildIndex()
        }
    }

    /**
     * 更新单个配置。
     */
    fun update(id: String, config: BloomConfig) {
        if (id.isBlank() || config.name.isBlank()) return
        synchronized(updateLock) {
            configs[id] = config
            rebuildIndex()
        }
    }

    /**
     * 删除配置。
     */
    fun remove(id: String) {
        synchronized(updateLock) {
            if (configs.remove(id) != null) {
                rebuildIndex()
            }
        }
    }

    /**
     * 清空所有配置。
     */
    fun clear() {
        synchronized(updateLock) {
            if (configs.isEmpty() && matchingIndex.entries.isEmpty()) return
            configs.clear()
            rebuildIndex()
        }
    }

    /**
     * 检查是否有配置。
     */
    fun hasConfigs(): Boolean = matchingIndex.entries.isNotEmpty()

    private fun rebuildIndex() {
        val nextRevision = matchingIndex.revision + 1L
        val groupedConfigs = configs.entries
            .groupBy { it.value.priority }
            .toSortedMap(compareByDescending<Int> { it })

        val priorityRanks = groupedConfigs.keys
            .sorted()
            .withIndex()
            .associate { (index, priority) -> priority to index + 1 }

        val groupsByPriority = LinkedHashMap<Int, List<BloomConfigMatch>>(groupedConfigs.size)
        val compiled = ArrayList<CompiledConfig>(configs.size)

        for ((priority, entries) in groupedConfigs) {
            val matches = entries
                .sortedBy { it.key }
                .map { (id, config) ->
                    BloomConfigMatch(
                        groupKey = id,
                        config = config,
                        priorityWeight = priorityRanks[priority] ?: 1
                    )
                }
            groupsByPriority[priority] = matches
            matches.forEach { match ->
                compiled.add(CompiledConfig(match, normalize(match.config.name)))
            }
        }

        matchCache.clear()
        matchingIndex = MatchingIndex(nextRevision, compiled, groupsByPriority)
    }

    private fun normalize(value: String?): String {
        return value?.trim()?.lowercase(Locale.ROOT).orEmpty()
    }
}
