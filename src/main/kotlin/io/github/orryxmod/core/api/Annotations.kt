package io.github.orryxmod.core.api

import kotlin.reflect.KClass

/**
 * 标记一个功能模块
 * @param id 功能唯一标识
 * @param description 功能描述
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Feature(
    val id: String,
    val description: String = ""
)

/**
 * 声明模块依赖
 * @param dependencies 依赖的模块类
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOn(vararg val dependencies: KClass<*>)

/**
 * 模块启用时调用
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnEnable

/**
 * 模块禁用时调用
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnDisable

/**
 * 客户端断开连接时调用
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnDisconnect

/**
 * 处理特定类型的网络包
 * @param packetType 要处理的包类型
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnPacket(val packetType: KClass<*>)

/**
 * 订阅事件
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subscribe
