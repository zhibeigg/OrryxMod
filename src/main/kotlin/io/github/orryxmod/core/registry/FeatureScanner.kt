package io.github.orryxmod.core.registry

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.DependsOn
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.feature.aim.AimFeature
import io.github.orryxmod.feature.bloom.BloomFeature
import io.github.orryxmod.feature.collider.ColliderFeature
import io.github.orryxmod.feature.effect.EffectFeature
import io.github.orryxmod.feature.mouse.MouseFeature
import io.github.orryxmod.feature.navigation.NavigationFeature
import io.github.orryxmod.feature.shockwave.ShockwaveFeature
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * 功能扫描器 - 自动发现并注册功能模块
 */
object FeatureScanner {

    /**
     * 已知的功能模块列表（手动注册，避免 Reflections 在 MC 环境下的问题）
     */
    private val knownFeatures: List<FeatureBase> = listOf(
        AimFeature,
        BloomFeature,
        ColliderFeature,
        EffectFeature,
        MouseFeature,
        NavigationFeature,
        ShockwaveFeature
    )

    /**
     * 扫描并注册所有功能模块
     * 按依赖关系拓扑排序后注册
     */
    fun scanAndRegister() {
        OrryxMod.logger.info("Registering features...")

        // 拓扑排序
        val sorted = topologicalSort(knownFeatures.map { it::class })

        // 按顺序注册
        for (featureClass in sorted) {
            try {
                val instance = knownFeatures.find { it::class == featureClass }
                if (instance != null) {
                    FeatureRegistry.register(instance)
                }
            } catch (ex: Exception) {
                OrryxMod.logger.error("Failed to register feature ${featureClass.simpleName}", ex)
            }
        }

        OrryxMod.logger.info("Feature registration complete, ${sorted.size} features registered")
    }

    /**
     * 拓扑排序 - 按依赖关系排序功能模块
     */
    private fun topologicalSort(classes: List<KClass<out FeatureBase>>): List<KClass<out FeatureBase>> {
        val result = mutableListOf<KClass<out FeatureBase>>()
        val visited = mutableSetOf<KClass<out FeatureBase>>()
        val visiting = mutableSetOf<KClass<out FeatureBase>>()

        fun visit(klass: KClass<out FeatureBase>) {
            if (klass in visited) return
            if (klass in visiting) {
                OrryxMod.logger.warn("Circular dependency detected for ${klass.simpleName}")
                return
            }

            visiting.add(klass)

            // 获取依赖
            val dependsOn = klass.findAnnotation<DependsOn>()
            dependsOn?.dependencies?.forEach { dep ->
                @Suppress("UNCHECKED_CAST")
                val depClass = dep as? KClass<out FeatureBase>
                if (depClass != null && depClass in classes) {
                    visit(depClass)
                }
            }

            visiting.remove(klass)
            visited.add(klass)
            result.add(klass)
        }

        classes.forEach { visit(it) }
        return result
    }
}
