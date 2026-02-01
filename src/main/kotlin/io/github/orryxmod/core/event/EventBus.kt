package io.github.orryxmod.core.event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * 事件处理器包装类
 */
private class EventHandler<T : Event>(
    val priority: Int,
    private val handler: (T) -> Unit
) {
    fun handle(event: T) = handler(event)
}

/**
 * 事件总线 - 模块间通信的核心
 * 使用线程安全的集合以支持多线程环境
 */
object EventBus {

    private val handlers = ConcurrentHashMap<KClass<out Event>, CopyOnWriteArrayList<EventHandler<*>>>()

    /**
     * 注册事件处理器（泛型版本）
     * @param priority 优先级，越大越先执行
     * @param handler 处理函数
     */
    inline fun <reified T : Event> subscribe(
        priority: Int = 0,
        noinline handler: (T) -> Unit
    ) {
        subscribe(T::class, priority, handler)
    }

    /**
     * 注册事件处理器
     * @param eventType 事件类型
     * @param priority 优先级，越大越先执行
     * @param handler 处理函数
     */
    fun <T : Event> subscribe(
        eventType: KClass<T>,
        priority: Int = 0,
        handler: (T) -> Unit
    ) {
        val list = handlers.getOrPut(eventType) { CopyOnWriteArrayList() }
        list.add(EventHandler(priority, handler))
        // 重新排序（CopyOnWriteArrayList 不支持 sortByDescending，需要替换整个列表）
        val sorted = list.sortedByDescending { (it as EventHandler<*>).priority }
        list.clear()
        list.addAll(sorted)
    }

    /**
     * 发布事件
     * @param event 要发布的事件
     * @return 事件本身（可能已被修改）
     */
    fun <T : Event> publish(event: T): T {
        val eventHandlers = handlers[event::class] ?: return event

        for (handler in eventHandlers) {
            @Suppress("UNCHECKED_CAST")
            (handler as EventHandler<T>).handle(event)

            // 如果事件被取消，停止传播
            if (event is CancellableEvent && event.cancelled) {
                break
            }
        }

        return event
    }

    /**
     * 移除指定事件类型的所有处理器
     */
    fun <T : Event> unsubscribeAll(eventType: KClass<T>) {
        handlers.remove(eventType)
    }

    /**
     * 清除所有处理器
     */
    fun clear() {
        handlers.clear()
    }
}
