package io.github.orryxmod.core.registry

import kotlin.reflect.KClass

/**
 * 服务定位器 - 简单的依赖注入容器
 * 用于管理全局服务实例
 */
object ServiceLocator {

    private val services = mutableMapOf<KClass<*>, Any>()

    /**
     * 注册服务实例（泛型版本）
     */
    inline fun <reified T : Any> register(instance: T) {
        register(T::class, instance)
    }

    /**
     * 注册服务实例
     */
    fun <T : Any> register(type: KClass<T>, instance: T) {
        services[type] = instance
    }

    /**
     * 获取服务实例（泛型版本，可能为 null）
     */
    inline fun <reified T : Any> get(): T? {
        return get(T::class)
    }

    /**
     * 获取服务实例（可能为 null）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): T? {
        return services[type] as? T
    }

    /**
     * 获取服务实例（泛型版本，不存在则抛异常）
     */
    inline fun <reified T : Any> require(): T {
        return require(T::class)
    }

    /**
     * 获取服务实例（不存在则抛异常）
     */
    fun <T : Any> require(type: KClass<T>): T {
        return get(type) ?: error("Service not found: ${type.simpleName}")
    }

    /**
     * 检查服务是否已注册
     */
    inline fun <reified T : Any> has(): Boolean {
        return has(T::class)
    }

    /**
     * 检查服务是否已注册
     */
    fun <T : Any> has(type: KClass<T>): Boolean {
        return services.containsKey(type)
    }

    /**
     * 移除服务
     */
    inline fun <reified T : Any> remove(): T? {
        return remove(T::class)
    }

    /**
     * 移除服务
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> remove(type: KClass<T>): T? {
        return services.remove(type) as? T
    }

    /**
     * 清除所有服务
     */
    fun clear() {
        services.clear()
    }
}
