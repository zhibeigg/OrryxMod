# OrryxMod 重构 Phase 1: 核心框架

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 构建 OrryxMod 的核心框架层，包括 API、事件系统、网络层、渲染系统和注册系统。

**Architecture:** 混合模式架构 - 核心依赖注入 + 模块间事件通信 + 功能实现函数组合。核心框架不含业务逻辑，只提供基础设施。

**Tech Stack:** Kotlin 1.9, Minecraft Forge 1.12.2, JOML, Reflections

---

## Task 1: 创建包结构

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/api/.gitkeep`
- Create: `src/main/kotlin/io/github/orryxmod/core/event/.gitkeep`
- Create: `src/main/kotlin/io/github/orryxmod/core/network/.gitkeep`
- Create: `src/main/kotlin/io/github/orryxmod/core/render/.gitkeep`
- Create: `src/main/kotlin/io/github/orryxmod/core/registry/.gitkeep`
- Create: `src/main/kotlin/io/github/orryxmod/core/handler/.gitkeep`
- Create: `src/main/kotlin/io/github/orryxmod/feature/.gitkeep`
- Create: `src/main/kotlin/io/github/orryxmod/shared/texture/.gitkeep`
- Create: `src/main/kotlin/io/github/orryxmod/shared/math/.gitkeep`

**Step 1: 创建目录结构**

```bash
mkdir -p src/main/kotlin/io/github/orryxmod/core/api
mkdir -p src/main/kotlin/io/github/orryxmod/core/event
mkdir -p src/main/kotlin/io/github/orryxmod/core/network
mkdir -p src/main/kotlin/io/github/orryxmod/core/render
mkdir -p src/main/kotlin/io/github/orryxmod/core/registry
mkdir -p src/main/kotlin/io/github/orryxmod/core/handler
mkdir -p src/main/kotlin/io/github/orryxmod/feature
mkdir -p src/main/kotlin/io/github/orryxmod/shared/texture
mkdir -p src/main/kotlin/io/github/orryxmod/shared/math
```

**Step 2: Commit**

```bash
git add -A
git commit -m "chore: create new package structure for refactor"
```

---

## Task 2: 核心注解定义

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/api/Annotations.kt`

**Step 1: 创建注解文件**

```kotlin
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
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/api/Annotations.kt
git commit -m "feat(core): add annotation definitions"
```

---

## Task 3: 功能模块基类

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/api/FeatureBase.kt`

**Step 1: 创建基类文件**

```kotlin
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
    var enabled: Boolean = true
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
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/api/FeatureBase.kt
git commit -m "feat(core): add FeatureBase class"
```

---

## Task 4: 可渲染效果接口

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/api/RenderableEffect.kt`

**Step 1: 创建接口文件**

```kotlin
package io.github.orryxmod.core.api

import io.github.orryxmod.core.render.RenderContext

/**
 * 可渲染效果接口
 * 所有需要渲染的视觉效果都应实现此接口
 */
interface RenderableEffect {

    /**
     * 效果唯一 ID
     */
    val id: String

    /**
     * 是否仍然活跃
     */
    val isActive: Boolean

    /**
     * 渲染优先级（越大越后渲染，即在上层）
     */
    val renderPriority: Int get() = 0

    /**
     * 执行渲染
     * @param context 渲染上下文
     */
    fun render(context: RenderContext)

    /**
     * 更新状态（每 tick 调用）
     */
    fun update()

    /**
     * 效果结束时清理资源
     */
    fun dispose() {}
}

/**
 * 带生命周期的效果基类
 * 用于有固定存活时间的效果
 */
abstract class TimedEffect(
    override val id: String,
    protected val lifetime: Int
) : RenderableEffect {

    protected var ticksAlive: Int = 0
        private set

    override val isActive: Boolean
        get() = ticksAlive < lifetime

    /**
     * 获取生命周期进度 (0.0 ~ 1.0)
     */
    val progress: Float
        get() = ticksAlive.toFloat() / lifetime.toFloat()

    override fun update() {
        ticksAlive++
    }
}
```

**Step 2: 验证编译**

注意：此文件依赖 `RenderContext`，暂时会有编译错误，Task 8 创建后即可编译。

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/api/RenderableEffect.kt
git commit -m "feat(core): add RenderableEffect interface"
```

---

## Task 5: 事件系统 - Event 接口

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/event/Event.kt`

**Step 1: 创建事件接口文件**

```kotlin
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
```

**Step 2: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/event/Event.kt
git commit -m "feat(core): add Event interfaces"
```

---

## Task 6: 事件系统 - EventBus

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/event/EventBus.kt`

**Step 1: 创建事件总线文件**

```kotlin
package io.github.orryxmod.core.event

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
 */
object EventBus {

    private val handlers = mutableMapOf<KClass<out Event>, MutableList<EventHandler<*>>>()

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
        val list = handlers.getOrPut(eventType) { mutableListOf() }
        list.add(EventHandler(priority, handler))
        list.sortByDescending { (it as EventHandler<*>).priority }
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
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/event/EventBus.kt
git commit -m "feat(core): add EventBus"
```

---

## Task 7: 事件系统 - 核心事件定义

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/event/Events.kt`

**Step 1: 创建核心事件定义文件**

```kotlin
package io.github.orryxmod.core.event

import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.render.RenderContext

/**
 * 核心事件定义
 */
object Events {

    /**
     * 功能模块启用
     */
    data class FeatureEnabled(val feature: FeatureBase) : Event

    /**
     * 功能模块禁用
     */
    data class FeatureDisabled(val feature: FeatureBase) : Event

    /**
     * 网络包接收（分发前，可拦截）
     */
    data class PacketReceived(
        val packet: OrryxPacket,
        override var cancelled: Boolean = false
    ) : CancellableEvent

    /**
     * 渲染效果添加
     */
    data class EffectAdded(val effect: RenderableEffect) : Event

    /**
     * 渲染效果移除
     */
    data class EffectRemoved(val effect: RenderableEffect) : Event

    /**
     * 客户端 Tick
     */
    data class ClientTick(val phase: Phase) : Event {
        enum class Phase { START, END }
    }

    /**
     * 世界渲染（RenderWorldLast）
     */
    data class WorldRender(
        val partialTicks: Float,
        val context: RenderContext
    ) : Event

    /**
     * 客户端断开连接
     */
    object ClientDisconnected : Event
}
```

**Step 2: Commit**

注意：此文件依赖 `OrryxPacket` 和 `RenderContext`，后续任务创建后即可编译。

```bash
git add src/main/kotlin/io/github/orryxmod/core/event/Events.kt
git commit -m "feat(core): add core event definitions"
```

---

## Task 8: 渲染系统 - RenderContext

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/render/RenderContext.kt`

**Step 1: 创建渲染上下文文件**

```kotlin
package io.github.orryxmod.core.render

import io.github.orryxmod.util.MC
import org.joml.Vector3d

/**
 * 渲染上下文 - 封装渲染时的常用数据
 */
data class RenderContext(
    val partialTicks: Float,
    val viewerX: Double,
    val viewerY: Double,
    val viewerZ: Double
) {
    /**
     * 将世界坐标转换为相对于观察者的坐标
     */
    fun toRelative(x: Double, y: Double, z: Double): Vector3d {
        return Vector3d(x - viewerX, y - viewerY, z - viewerZ)
    }

    /**
     * 将世界坐标转换为相对于观察者的坐标
     */
    fun toRelative(pos: Vector3d): Vector3d {
        return toRelative(pos.x, pos.y, pos.z)
    }

    companion object {
        /**
         * 从当前渲染状态创建上下文
         */
        fun create(partialTicks: Float): RenderContext {
            val rm = MC.renderManager
            return RenderContext(
                partialTicks = partialTicks,
                viewerX = rm.viewerPosX,
                viewerY = rm.viewerPosY,
                viewerZ = rm.viewerPosZ
            )
        }
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/render/RenderContext.kt
git commit -m "feat(core): add RenderContext"
```

---

## Task 9: 渲染系统 - RenderUtils

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/render/RenderUtils.kt`

**Step 1: 创建渲染工具文件**

```kotlin
package io.github.orryxmod.core.render

import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.opengl.GL11

/**
 * 渲染工具函数
 */
object RenderUtils {

    /**
     * 安全的 GL 状态管理
     * 自动保存和恢复 GL 状态
     */
    inline fun withGlState(
        blend: Boolean = false,
        depth: Boolean = true,
        lighting: Boolean = false,
        texture: Boolean = true,
        block: () -> Unit
    ) {
        GlStateManager.pushMatrix()

        if (blend) {
            GlStateManager.enableBlend()
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        }
        if (!depth) GlStateManager.disableDepth()
        if (!lighting) GlStateManager.disableLighting()
        if (!texture) GlStateManager.disableTexture2D()

        try {
            block()
        } finally {
            if (!texture) GlStateManager.enableTexture2D()
            if (!lighting) GlStateManager.enableLighting()
            if (!depth) GlStateManager.enableDepth()
            if (blend) GlStateManager.disableBlend()

            GlStateManager.popMatrix()
        }
    }

    /**
     * 绘制纹理四边形（水平面）
     */
    fun drawTexturedQuadHorizontal(
        x: Double, y: Double, z: Double,
        width: Double, height: Double,
        u0: Double = 0.0, v0: Double = 0.0,
        u1: Double = 1.0, v1: Double = 1.0
    ) {
        val hw = width / 2
        val hh = height / 2

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(x - hw, y, z + hh).tex(u0, v1).endVertex()
        buffer.pos(x + hw, y, z + hh).tex(u1, v1).endVertex()
        buffer.pos(x + hw, y, z - hh).tex(u1, v0).endVertex()
        buffer.pos(x - hw, y, z - hh).tex(u0, v0).endVertex()
        tessellator.draw()
    }

    /**
     * 绘制纹理四边形（垂直面，面向观察者）
     */
    fun drawTexturedQuadVertical(
        x: Double, y: Double, z: Double,
        width: Double, height: Double,
        u0: Double = 0.0, v0: Double = 0.0,
        u1: Double = 1.0, v1: Double = 1.0
    ) {
        val hw = width / 2

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(x - hw, y + height, z).tex(u0, v0).endVertex()
        buffer.pos(x + hw, y + height, z).tex(u1, v0).endVertex()
        buffer.pos(x + hw, y, z).tex(u1, v1).endVertex()
        buffer.pos(x - hw, y, z).tex(u0, v1).endVertex()
        tessellator.draw()
    }

    /**
     * 渲染实体
     */
    fun renderEntity(
        entity: EntityLivingBase,
        x: Double, y: Double, z: Double,
        yaw: Float = 0f,
        scale: Float = 1f
    ) {
        val renderManager = MC.renderManager

        withGlState(blend = true) {
            GlStateManager.translate(x, y, z)
            GlStateManager.scale(scale, scale, scale)

            val prevShadow = renderManager.isRenderShadow
            renderManager.isRenderShadow = false
            renderManager.renderEntity(entity, 0.0, 0.0, 0.0, yaw, 1f, false)
            renderManager.isRenderShadow = prevShadow
        }
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/render/RenderUtils.kt
git commit -m "feat(core): add RenderUtils"
```

---

## Task 10: 渲染系统 - EffectManager

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/render/EffectManager.kt`

**Step 1: 创建效果管理器文件**

```kotlin
package io.github.orryxmod.core.render

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events

/**
 * 效果管理器 - 统一管理所有可渲染效果
 */
object EffectManager {

    private val effects = mutableListOf<RenderableEffect>()
    private val pendingAdd = mutableListOf<RenderableEffect>()
    private val pendingRemove = mutableListOf<RenderableEffect>()

    /**
     * 添加效果
     */
    fun add(effect: RenderableEffect) {
        pendingAdd.add(effect)
        EventBus.publish(Events.EffectAdded(effect))
    }

    /**
     * 移除效果
     */
    fun remove(effect: RenderableEffect) {
        pendingRemove.add(effect)
    }

    /**
     * 按 ID 移除效果
     */
    fun removeById(id: String) {
        effects.filter { it.id == id }.forEach { remove(it) }
    }

    /**
     * 按类型移除效果
     */
    inline fun <reified T : RenderableEffect> removeByType() {
        effects.filterIsInstance<T>().forEach { remove(it) }
    }

    /**
     * 获取所有指定类型的效果
     */
    inline fun <reified T : RenderableEffect> getByType(): List<T> {
        return effects.filterIsInstance<T>()
    }

    /**
     * 检查是否存在指定 ID 的效果
     */
    fun exists(id: String): Boolean {
        return effects.any { it.id == id } || pendingAdd.any { it.id == id }
    }

    /**
     * 每 tick 更新
     */
    fun update() {
        // 处理待添加
        if (pendingAdd.isNotEmpty()) {
            effects.addAll(pendingAdd)
            effects.sortBy { it.renderPriority }
            pendingAdd.clear()
        }

        // 更新所有效果
        effects.forEach { effect ->
            try {
                effect.update()
            } catch (ex: Exception) {
                OrryxMod.logger.error("Error updating effect ${effect.id}", ex)
            }
        }

        // 移除失效的效果
        val expired = effects.filter { !it.isActive }
        expired.forEach { effect ->
            effect.dispose()
            EventBus.publish(Events.EffectRemoved(effect))
        }
        effects.removeAll(expired.toSet())

        // 处理待移除
        if (pendingRemove.isNotEmpty()) {
            pendingRemove.forEach { effect ->
                if (effects.remove(effect)) {
                    effect.dispose()
                    EventBus.publish(Events.EffectRemoved(effect))
                }
            }
            pendingRemove.clear()
        }
    }

    /**
     * 渲染所有效果
     */
    fun render(context: RenderContext) {
        if (effects.isEmpty()) return

        effects.forEach { effect ->
            if (effect.isActive) {
                try {
                    effect.render(context)
                } catch (ex: Exception) {
                    OrryxMod.logger.error("Error rendering effect ${effect.id}", ex)
                }
            }
        }
    }

    /**
     * 清除所有效果
     */
    fun clear() {
        effects.forEach { it.dispose() }
        effects.clear()
        pendingAdd.clear()
        pendingRemove.clear()
    }

    /**
     * 获取当前效果数量
     */
    val size: Int get() = effects.size
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/render/EffectManager.kt
git commit -m "feat(core): add EffectManager"
```

---

## Task 11: 网络层 - OrryxPacket 密封类

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/network/OrryxPacket.kt`

**Step 1: 创建协议密封类文件**

```kotlin
package io.github.orryxmod.core.network

import java.util.UUID

/**
 * 协议密封类 - 所有网络包的基类
 * 使用密封类确保类型安全和 when 穷举
 */
sealed class OrryxPacket {
    abstract val packetId: Int

    // ========== 瞄准系统 ==========

    data class AimRequest(
        val skill: String,
        val module: String,
        val scale: Double,
        val maxDistance: Double
    ) : OrryxPacket() {
        override val packetId = 1
    }

    data class AimConfirm(
        val confirmed: Boolean
    ) : OrryxPacket() {
        override val packetId = 2
    }

    data class AimResponse(
        val skill: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    ) : OrryxPacket() {
        override val packetId = 4
    }

    // ========== 实体效果 ==========

    data class GhostEffect(
        val uuid: UUID,
        val timeout: Long,
        val density: Int,
        val gap: Int
    ) : OrryxPacket() {
        override val packetId = 3
    }

    data class FlickerEffect(
        val uuid: UUID,
        val timeout: Long,
        val alpha: Float
    ) : OrryxPacket() {
        override val packetId = 5
    }

    data class EntityShowAdd(
        val uuid: UUID,
        val group: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val timeout: Long,
        val rotateX: Float,
        val rotateY: Float,
        val rotateZ: Float,
        val scale: Float
    ) : OrryxPacket() {
        override val packetId = 8
    }

    data class EntityShowRemove(
        val uuid: UUID,
        val group: String
    ) : OrryxPacket() {
        override val packetId = 9
    }

    // ========== 鼠标控制 ==========

    data class MouseControl(
        val show: Boolean
    ) : OrryxPacket() {
        override val packetId = 7
    }

    // ========== 导航系统 ==========

    data class NavigationStart(
        val x: Int,
        val y: Int,
        val z: Int,
        val range: Int
    ) : OrryxPacket() {
        override val packetId = 10
    }

    object NavigationStop : OrryxPacket() {
        override val packetId = 11
    }

    // ========== 冲击波系统 ==========

    data class SquareShockwave(
        val x: Double,
        val y: Double,
        val z: Double,
        val length: Double,
        val width: Double,
        val yaw: Double
    ) : OrryxPacket() {
        override val packetId = 12
    }

    data class CircleShockwave(
        val x: Double,
        val y: Double,
        val z: Double,
        val radius: Double
    ) : OrryxPacket() {
        override val packetId = 13
    }

    data class SectorShockwave(
        val x: Double,
        val y: Double,
        val z: Double,
        val radius: Double,
        val angle: Double,
        val yaw: Double
    ) : OrryxPacket() {
        override val packetId = 14
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/network/OrryxPacket.kt
git commit -m "feat(core): add OrryxPacket sealed class"
```

---

## Task 12: 网络层 - PacketCodec

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/network/PacketCodec.kt`

**Step 1: 创建编解码器文件**

```kotlin
package io.github.orryxmod.core.network

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import io.github.orryxmod.OrryxMod
import java.util.UUID

/**
 * 协议编解码器
 */
object PacketCodec {

    /**
     * 解码：字节流 -> OrryxPacket
     */
    fun decode(input: ByteArrayDataInput): OrryxPacket? {
        return try {
            when (val id = input.readInt()) {
                1 -> OrryxPacket.AimRequest(
                    skill = input.readUTF(),
                    module = input.readUTF(),
                    scale = input.readDouble(),
                    maxDistance = input.readDouble()
                )
                2 -> OrryxPacket.AimConfirm(
                    confirmed = input.readBoolean()
                )
                3 -> OrryxPacket.GhostEffect(
                    uuid = input.readUUID(),
                    timeout = input.readLong().coerceIn(0, 60_000),
                    density = input.readInt().coerceIn(1, 50),
                    gap = input.readInt().coerceIn(0, 20)
                )
                5 -> OrryxPacket.FlickerEffect(
                    uuid = input.readUUID(),
                    timeout = input.readLong().coerceIn(0, 60_000),
                    alpha = input.readFloat().coerceIn(0f, 1f)
                )
                7 -> OrryxPacket.MouseControl(
                    show = input.readBoolean()
                )
                8 -> OrryxPacket.EntityShowAdd(
                    uuid = input.readUUID(),
                    group = input.readUTF(),
                    x = input.readDouble(),
                    y = input.readDouble(),
                    z = input.readDouble(),
                    timeout = input.readLong().coerceIn(0, 300_000),
                    rotateX = input.readFloat(),
                    rotateY = input.readFloat(),
                    rotateZ = input.readFloat(),
                    scale = input.readFloat().coerceIn(0.01f, 10f)
                )
                9 -> OrryxPacket.EntityShowRemove(
                    uuid = input.readUUID(),
                    group = input.readUTF()
                )
                10 -> OrryxPacket.NavigationStart(
                    x = input.readInt(),
                    y = input.readInt(),
                    z = input.readInt(),
                    range = input.readInt().coerceIn(0, 100)
                )
                11 -> OrryxPacket.NavigationStop
                12 -> OrryxPacket.SquareShockwave(
                    x = input.readDouble(),
                    y = input.readDouble(),
                    z = input.readDouble(),
                    length = input.readDouble().coerceIn(0.5, 100.0),
                    width = input.readDouble().coerceIn(0.5, 100.0),
                    yaw = input.readDouble()
                )
                13 -> OrryxPacket.CircleShockwave(
                    x = input.readDouble(),
                    y = input.readDouble(),
                    z = input.readDouble(),
                    radius = input.readDouble().coerceIn(0.5, 100.0)
                )
                14 -> OrryxPacket.SectorShockwave(
                    x = input.readDouble(),
                    y = input.readDouble(),
                    z = input.readDouble(),
                    radius = input.readDouble().coerceIn(0.5, 100.0),
                    angle = input.readDouble().coerceIn(0.0, 360.0),
                    yaw = input.readDouble()
                )
                else -> {
                    OrryxMod.logger.warn("Unknown packet ID: $id")
                    null
                }
            }
        } catch (ex: Exception) {
            OrryxMod.logger.error("Error decoding packet", ex)
            null
        }
    }

    /**
     * 编码：OrryxPacket -> 字节流
     */
    fun encode(packet: OrryxPacket, output: ByteArrayDataOutput) {
        output.writeInt(packet.packetId)
        when (packet) {
            is OrryxPacket.AimResponse -> {
                output.writeUTF(packet.skill)
                output.writeDouble(packet.x)
                output.writeDouble(packet.y)
                output.writeDouble(packet.z)
                output.writeFloat(packet.yaw)
                output.writeFloat(packet.pitch)
            }
            else -> {
                // 目前只有 AimResponse 需要客户端发送
            }
        }
    }

    /**
     * 从输入流读取 UUID
     */
    private fun ByteArrayDataInput.readUUID(): UUID {
        val str = readUTF()
        return runCatching { UUID.fromString(str) }.getOrElse {
            throw IllegalArgumentException("Invalid UUID format: $str")
        }
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/network/PacketCodec.kt
git commit -m "feat(core): add PacketCodec"
```

---

## Task 13: 网络层 - PacketDispatcher

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/network/PacketDispatcher.kt`

**Step 1: 创建包分发器文件**

```kotlin
package io.github.orryxmod.core.network

import com.google.common.io.ByteStreams
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket
import kotlin.reflect.KClass

/**
 * 包处理器包装类
 */
private class PacketHandler<T : OrryxPacket>(
    private val handler: (T) -> Unit
) {
    fun handle(packet: T) = handler(packet)
}

/**
 * 协议分发器
 */
object PacketDispatcher {

    private val handlers = mutableMapOf<KClass<out OrryxPacket>, MutableList<PacketHandler<*>>>()

    /**
     * 注册处理器（泛型版本）
     */
    inline fun <reified T : OrryxPacket> register(noinline handler: (T) -> Unit) {
        register(T::class, handler)
    }

    /**
     * 注册处理器
     */
    fun <T : OrryxPacket> register(type: KClass<T>, handler: (T) -> Unit) {
        handlers.getOrPut(type) { mutableListOf() }
            .add(PacketHandler(handler))
    }

    /**
     * 分发协议包
     */
    fun dispatch(packet: OrryxPacket) {
        // 先发布事件，允许拦截
        val event = EventBus.publish(Events.PacketReceived(packet))
        if (event.cancelled) return

        // 分发到注册的处理器
        val packetHandlers = handlers[packet::class] ?: return

        for (handler in packetHandlers) {
            try {
                @Suppress("UNCHECKED_CAST")
                (handler as PacketHandler<OrryxPacket>).handle(packet)
            } catch (ex: Exception) {
                OrryxMod.logger.error("Error handling packet ${packet::class.simpleName}", ex)
            }
        }
    }

    /**
     * 发送协议包到服务器
     */
    fun send(packet: OrryxPacket) {
        try {
            val output = ByteStreams.newDataOutput()
            PacketCodec.encode(packet, output)
            OrryxMod.network.sendToServer(
                FMLProxyPacket(
                    PacketBuffer(Unpooled.wrappedBuffer(output.toByteArray())),
                    "${OrryxMod.MOD_ID}:main"
                )
            )
        } catch (ex: Exception) {
            OrryxMod.logger.error("Failed to send packet: ${ex.message}")
        }
    }

    /**
     * 清除所有处理器
     */
    fun clear() {
        handlers.clear()
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/network/PacketDispatcher.kt
git commit -m "feat(core): add PacketDispatcher"
```

---

## Task 14: 网络层 - NetworkHandler

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/core/network/NetworkHandler.kt`

**Step 1: 创建网络事件处理器文件**

```kotlin
package io.github.orryxmod.core.network

import com.google.common.io.ByteStreams
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.util.MC
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent

/**
 * 网络处理入口（Forge 事件监听）
 */
object NetworkHandler {

    @SubscribeEvent
    fun onPacketReceived(event: FMLNetworkEvent.ClientCustomPacketEvent) {
        try {
            val fmlPacket = event.packet
            if (fmlPacket.channel() != "${OrryxMod.MOD_ID}:main") return

            val buffer = fmlPacket.payload()
            val bytes = ByteArray(buffer.readableBytes())
            buffer.readBytes(bytes)

            val input = ByteStreams.newDataInput(bytes)
            val packet = PacketCodec.decode(input) ?: return

            // 在主线程处理
            MC.addScheduledTask {
                PacketDispatcher.dispatch(packet)
            }
        } catch (ex: Exception) {
            OrryxMod.logger.error("Error processing packet", ex)
        }
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/network/NetworkHandler.kt
git commit -m "feat(core): add NetworkHandler"
```

---

## Phase 1 完成检查点

**验证所有文件编译通过：**

Run: `./gradlew.bat compileKotlin`
Expected: BUILD SUCCESSFUL

**创建 Phase 1 完成标记提交：**

```bash
git add -A
git commit -m "feat: complete Phase 1 - core framework"
```

---

## 后续 Phase

- **Phase 2:** `core/registry/` - FeatureRegistry, FeatureScanner, ServiceLocator
- **Phase 3:** `core/handler/` - CommandHandler, DisconnectHandler, WorldRenderHandler
- **Phase 4:** `shared/` - TextureManager, VectorExtensions
- **Phase 5:** `feature/effect/` - EffectFeature (Ghost/Flicker/EntityShow)
- **Phase 6:** `feature/aim/` - AimFeature
- **Phase 7:** `feature/shockwave/` - ShockwaveFeature (DSL)
- **Phase 8:** `feature/navigation/`, `feature/mouse/` - 其他功能
- **Phase 9:** 集成和迁移 - 更新 OrryxMod.kt，删除旧代码
