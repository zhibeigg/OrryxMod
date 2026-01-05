package io.github.orryxmod.core.registry

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.DependsOn
import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import org.reflections.Reflections
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * 功能扫描器 - 自动发现并注册功能模块
 */
object FeatureScanner {

    private const val BASE_PACKAGE = "io.github.orryxmod"

    /**
     * 扫描并注册所有功能模块
     * 按依赖关系拓扑排序后注册
     */
    fun scanAndRegister() {
        OrryxMod.logger.info("Scanning for features...")

        val reflections = Reflections(BASE_PACKAGE)
        val featureClasses = reflections.getTypesAnnotatedWith(Feature::class.java)
            .filter { FeatureBase::class.java.isAssignableFrom(it) }
            .mapNotNull { it.kotlin as? KClass<out FeatureBase> }

        OrryxMod.logger.info("Found ${featureClasses.size} feature classes")

        // 拓扑排序
        val sorted = topologicalSort(featureClasses)

        // 按顺序注册
        for (featureClass in sorted) {
            try {
                val instance = getOrCreateInstance(featureClass)
                if (instance != null) {
                    FeatureRegistry.register(instance)
                }
            } catch (ex: Exception) {
                OrryxMod.logger.error("Failed to instantiate feature ${featureClass.simpleName}", ex)
            }
        }

        OrryxMod.logger.info("Feature scanning complete, ${sorted.size} features registered")
    }

    /**
     * 获取或创建功能模块实例
     * 支持 object 单例和普通类
     */
    private fun getOrCreateInstance(klass: KClass<out FeatureBase>): FeatureBase? {
        // 优先使用 object 实例
        klass.objectInstance?.let { return it }

        // 尝试无参构造函数
        return try {
            klass.java.getDeclaredConstructor().newInstance()
        } catch (ex: Exception) {
            OrryxMod.logger.error("Cannot instantiate ${klass.simpleName}: no default constructor", ex)
            null
        }
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
