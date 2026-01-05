package io.github.orryxmod.core.event

/**
 * 事件基接口
 */
interface Event

/**
 * 可取消的事件
 */
interface CancellableEvent : Event {
    var cancelled: Boolean
}
