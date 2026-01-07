package io.github.orryxmod.feature.bloom

/**
 * Bloom 配置管理器
 */
object BloomConfigManager {
    private val configs = mutableMapOf<String, BloomConfig>()

    /**
     * 为实体查找匹配的配置（按优先级排序后取最高优先级）
     */
    fun findConfig(entityName: String): BloomConfig? {
        return configs.values
            .filter { entityName.contains(it.name, ignoreCase = true) }
            .maxByOrNull { it.priority }
    }

    /**
     * 全量同步配置
     */
    fun syncAll(newConfigs: Map<String, BloomConfig>) {
        configs.clear()
        configs.putAll(newConfigs)
    }

    /**
     * 更新单个配置
     */
    fun update(id: String, config: BloomConfig) {
        configs[id] = config
    }

    /**
     * 删除配置
     */
    fun remove(id: String) {
        configs.remove(id)
    }

    /**
     * 清空所有配置
     */
    fun clear() {
        configs.clear()
    }

    /**
     * 检查是否有配置
     */
    fun hasConfigs(): Boolean = configs.isNotEmpty()
}
