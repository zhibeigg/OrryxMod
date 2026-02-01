package io.github.orryxmod.core.registry

import kotlin.reflect.KClass

/**
 * 可释放资源接口
 * 实现此接口的服务在被移除时会自动调用 dispose() 方法
 */
interface Disposable {
    fun dispose()
}

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
        // 如果已存在同类型服务，先释放旧服务
        val existing = services[type]
        if (existing is Disposable) {
            existing.dispose()
        }
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
     * 移除服务，如果服务实现了 Disposable 接口则自动调用 dispose()
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> remove(type: KClass<T>): T? {
        val service = services.remove(type) as? T
        if (service is Disposable) {
            service.dispose()
        }
        return service
    }

    /**
     * 清除所有服务，自动释放实现了 Disposable 接口的服务
     */
    fun clear() {
        services.values.forEach { service ->
            if (service is Disposable) {
                service.dispose()
            }
        }
        services.clear()
    }
}
