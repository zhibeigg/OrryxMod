package io.github.orryxmod.core.registry

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisable
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnEnable
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.api.Subscribe
import io.github.orryxmod.core.event.Event
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.network.PacketDispatcher
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.valueParameters

/**
 * 功能注册表 - 管理所有功能模块
 */
object FeatureRegistry {

    private val features = mutableMapOf<String, FeatureBase>()
    private val featuresByClass = mutableMapOf<KClass<out FeatureBase>, FeatureBase>()

    /**
     * 注册功能模块
     */
    fun register(feature: FeatureBase) {
        val id = feature.metadata.id
        if (features.containsKey(id)) {
            OrryxMod.logger.warn("Feature '$id' is already registered, skipping")
            return
        }

        features[id] = feature
        featuresByClass[feature::class] = feature
        OrryxMod.logger.info("Registered feature: $id")

        // 注册该模块的处理器
        registerHandlers(feature)
    }

    /**
     * 按 ID 获取功能模块
     */
    fun get(id: String): FeatureBase? = features[id]

    /**
     * 按类型获取功能模块
     */
    inline fun <reified T : FeatureBase> get(): T? = get(T::class)

    /**
     * 按类型获取功能模块
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : FeatureBase> get(type: KClass<T>): T? = featuresByClass[type] as? T

    /**
     * 获取所有已注册的功能模块
     */
    fun getAll(): Collection<FeatureBase> = features.values

    /**
     * 启用所有功能模块
     */
    fun enableAll() {
        features.values.forEach { feature ->
            try {
                feature.enable()
                invokeLifecycleMethods(feature, OnEnable::class)
                EventBus.publish(Events.FeatureEnabled(feature))
            } catch (ex: Exception) {
                OrryxMod.logger.error("Error enabling feature ${feature.metadata.id}", ex)
            }
        }
    }

    /**
     * 禁用所有功能模块
     */
    fun disableAll() {
        features.values.forEach { feature ->
            try {
                invokeLifecycleMethods(feature, OnDisable::class)
                feature.disable()
                EventBus.publish(Events.FeatureDisabled(feature))
            } catch (ex: Exception) {
                OrryxMod.logger.error("Error disabling feature ${feature.metadata.id}", ex)
            }
        }
    }

    /**
     * 通知所有模块客户端断开连接
     */
    fun notifyDisconnect() {
        features.values.forEach { feature ->
            try {
                invokeLifecycleMethods(feature, OnDisconnect::class)
            } catch (ex: Exception) {
                OrryxMod.logger.error("Error notifying disconnect for ${feature.metadata.id}", ex)
            }
        }
    }

    /**
     * 清除所有功能模块
     */
    fun clear() {
        features.clear()
        featuresByClass.clear()
        // 同步清理 EventBus 和 PacketDispatcher 中的 handler，
        // 防止 Feature 重新注册时旧 handler 仍然存在导致重复执行
        EventBus.clear()
        PacketDispatcher.clear()
    }

    /**
     * 为功能模块注册处理器
     */
    private fun registerHandlers(feature: FeatureBase) {
        val klass = feature::class

        for (func in klass.functions) {
            // 注册网络包处理器
            func.findAnnotation<OnPacket>()?.let { annotation ->
                @Suppress("UNCHECKED_CAST")
                val packetType = annotation.packetType as KClass<out OrryxPacket>
                PacketDispatcher.register(packetType) { packet ->
                    if (feature.enabled) {
                        try {
                            func.call(feature, packet)
                        } catch (ex: Exception) {
                            OrryxMod.logger.error("Error in packet handler ${func.name}", ex)
                        }
                    }
                }
            }

            // 注册事件订阅器
            func.findAnnotation<Subscribe>()?.let {
                val params = func.valueParameters
                if (params.size == 1) {
                    val eventType = params[0].type.classifier as? KClass<*>
                    if (eventType != null && Event::class.java.isAssignableFrom(eventType.java)) {
                        @Suppress("UNCHECKED_CAST")
                        EventBus.subscribe(eventType as KClass<Event>) { event ->
                            if (feature.enabled) {
                                try {
                                    func.call(feature, event)
                                } catch (ex: Exception) {
                                    OrryxMod.logger.error("Error in event handler ${func.name}", ex)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 调用生命周期方法
     */
    private fun invokeLifecycleMethods(feature: FeatureBase, annotationType: KClass<out Annotation>) {
        feature::class.functions
            .filter { it.findAnnotation(annotationType) != null }
            .forEach { func ->
                try {
                    func.call(feature)
                } catch (ex: Exception) {
                    OrryxMod.logger.error("Error invoking lifecycle method ${func.name}", ex)
                }
            }
    }

    /**
     * 扩展函数：查找指定类型的注解
     */
    private fun <T : Annotation> kotlin.reflect.KFunction<*>.findAnnotation(type: KClass<T>): T? {
        return annotations.filterIsInstance(type.java).firstOrNull()
    }
}
