package io.github.orryxmod.core.api

import kotlin.reflect.full.findAnnotation

/**
 * 功能模块元数据
 */
data class FeatureMetadata(
    val id: String,
    val description: String
)

/**
 * 功能模块基类
 * 所有功能模块都应继承此类
 */
abstract class FeatureBase {

    /**
     * 功能是否启用
     */
    var enabled: Boolean = false
        protected set

    /**
     * 获取功能元数据（从 @Feature 注解解析）
     */
    val metadata: FeatureMetadata by lazy {
        val annotation = this::class.findAnnotation<Feature>()
            ?: error("Feature class ${this::class.simpleName} must be annotated with @Feature")
        FeatureMetadata(annotation.id, annotation.description)
    }

    /**
     * 启用功能
     */
    open fun enable() {
        enabled = true
    }

    /**
     * 禁用功能
     */
    open fun disable() {
        enabled = false
    }

    /**
     * 测试方法 - 子类可重写用于调试
     */
    open fun test() {}
}
