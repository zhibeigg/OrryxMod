package io.github.orryxmod.feature.bloom

import java.util.concurrent.ConcurrentHashMap

/**
 * Bloom 配置管理器
 * 使用线程安全的集合以支持多线程环境（网络线程写入，渲染线程读取）
 */
object BloomConfigManager {
    private val configs = ConcurrentHashMap<String, BloomConfig>()

    /**
     * 为实体查找匹配的配置（按优先级排序后取最高优先级）
     */
    fun findConfig(entityName: String): BloomConfig? {
        // 创建快照进行遍历，避免并发修改
        return configs.values.toList()
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
