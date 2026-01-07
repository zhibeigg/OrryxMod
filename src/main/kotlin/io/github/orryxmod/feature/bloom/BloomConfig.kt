package io.github.orryxmod.feature.bloom

/**
 * Bloom 配置项
 */
data class BloomConfig(
    val name: String,           // 匹配关键词
    val color: IntArray,        // RGBA [r, g, b, a]
    val strength: Float,        // 泛光强度
    val radius: Float,          // 渲染距离
    val priority: Int           // 优先级
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BloomConfig) return false
        return name == other.name && color.contentEquals(other.color) &&
               strength == other.strength && radius == other.radius && priority == other.priority
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + color.contentHashCode()
        result = 31 * result + strength.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + priority
        return result
    }
}
