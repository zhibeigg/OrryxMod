# OrryxMod 全面重构设计

## 概述

对 OrryxMod 进行全面架构重构，采用混合模式设计（核心依赖注入 + 模块间事件通信 + 功能实现函数组合），实现可扩展性、可配置性、代码清晰度和性能优化。

## 设计决策

| 方面 | 选择 |
|------|------|
| API 风格 | DSL 风格 |
| 架构理念 | 混合模式 |
| 包结构 | 混合分包（核心按层，功能按模块） |
| 模块生命周期 | 注解驱动 |
| 网络协议 | 密封类层级 |
| 渲染系统 | 实体化渲染 |
| 配置系统 | 配置数据类 |

---

## 一、核心 API 层

### 注解定义

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class Feature(val id: String, val description: String = "")

@Target(AnnotationTarget.CLASS)
annotation class DependsOn(vararg val dependencies: KClass<*>)

@Target(AnnotationTarget.FUNCTION)
annotation class OnEnable

@Target(AnnotationTarget.FUNCTION)
annotation class OnDisable

@Target(AnnotationTarget.FUNCTION)
annotation class OnPacket(val packetType: KClass<out OrryxPacket>)

@Target(AnnotationTarget.FUNCTION)
annotation class Subscribe

@Target(AnnotationTarget.FUNCTION)
annotation class OnDisconnect
```

### 功能模块基类

```kotlin
abstract class FeatureBase {
    var enabled: Boolean = true
        protected set

    val metadata: FeatureMetadata by lazy {
        val annotation = this::class.findAnnotation<Feature>()
            ?: error("Feature class must be annotated with @Feature")
        FeatureMetadata(annotation.id, annotation.description)
    }

    open fun test() {}
}
```

### 可渲染效果接口

```kotlin
interface RenderableEffect {
    val id: String
    val isActive: Boolean
    val renderPriority: Int get() = 0

    fun render(context: RenderContext)
    fun update()
    fun dispose() {}
}

abstract class TimedEffect(
    override val id: String,
    protected val lifetime: Int
) : RenderableEffect {
    protected var ticksAlive: Int = 0
    override val isActive: Boolean get() = ticksAlive < lifetime
    override fun update() { ticksAlive++ }
}
```

---

## 二、事件系统

```kotlin
interface Event
interface CancellableEvent : Event {
    var cancelled: Boolean
}

object EventBus {
    inline fun <reified T : Event> subscribe(priority: Int = 0, noinline handler: (T) -> Unit)
    fun <T : Event> publish(event: T): T
}

object Events {
    data class FeatureEnabled(val feature: FeatureBase) : Event
    data class FeatureDisabled(val feature: FeatureBase) : Event
    data class PacketReceived(val packet: OrryxPacket, override var cancelled: Boolean = false) : CancellableEvent
    data class EffectAdded(val effect: RenderableEffect) : Event
    data class EffectRemoved(val effect: RenderableEffect) : Event
    data class ClientTick(val phase: Phase) : Event { enum class Phase { START, END } }
    data class WorldRender(val partialTicks: Float, val context: RenderContext) : Event
    object ClientDisconnected : Event
}
```

---

## 三、网络层

### 密封类协议定义

```kotlin
sealed class OrryxPacket {
    abstract val packetId: Int

    // 瞄准系统
    data class AimRequest(val skill: String, val module: String, val scale: Double, val maxDistance: Double) : OrryxPacket() { override val packetId = 1 }
    data class AimConfirm(val confirmed: Boolean) : OrryxPacket() { override val packetId = 2 }
    data class AimResponse(val skill: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) : OrryxPacket() { override val packetId = 4 }

    // 实体效果
    data class GhostEffect(val uuid: UUID, val timeout: Long, val density: Int, val gap: Int) : OrryxPacket() { override val packetId = 3 }
    data class FlickerEffect(val uuid: UUID, val timeout: Long, val alpha: Float) : OrryxPacket() { override val packetId = 5 }
    data class EntityShowAdd(...) : OrryxPacket() { override val packetId = 8 }
    data class EntityShowRemove(val uuid: UUID, val group: String) : OrryxPacket() { override val packetId = 9 }

    // 导航
    data class NavigationStart(val x: Int, val y: Int, val z: Int, val range: Int) : OrryxPacket() { override val packetId = 10 }
    object NavigationStop : OrryxPacket() { override val packetId = 11 }

    // 冲击波
    data class SquareShockwave(val x: Double, val y: Double, val z: Double, val length: Double, val width: Double, val yaw: Double) : OrryxPacket() { override val packetId = 12 }
    data class CircleShockwave(val x: Double, val y: Double, val z: Double, val radius: Double) : OrryxPacket() { override val packetId = 13 }
    data class SectorShockwave(val x: Double, val y: Double, val z: Double, val radius: Double, val angle: Double, val yaw: Double) : OrryxPacket() { override val packetId = 14 }

    // 鼠标
    data class MouseControl(val show: Boolean) : OrryxPacket() { override val packetId = 7 }
}
```

### 协议分发

```kotlin
object PacketDispatcher {
    inline fun <reified T : OrryxPacket> register(noinline handler: (T) -> Unit)
    fun dispatch(packet: OrryxPacket)
    fun send(packet: OrryxPacket)
}
```

---

## 四、渲染系统

### 渲染上下文

```kotlin
data class RenderContext(
    val partialTicks: Float,
    val viewerX: Double,
    val viewerY: Double,
    val viewerZ: Double
) {
    fun toRelative(x: Double, y: Double, z: Double): Vector3d
}
```

### 效果管理器

```kotlin
object EffectManager {
    fun add(effect: RenderableEffect)
    fun remove(effect: RenderableEffect)
    fun removeById(id: String)
    inline fun <reified T : RenderableEffect> removeByType()
    inline fun <reified T : RenderableEffect> getByType(): List<T>
    fun update()  // 每 tick 调用
    fun render(context: RenderContext)  // 渲染时调用
    fun clear()
}
```

### 渲染工具

```kotlin
object RenderUtils {
    inline fun withGlState(blend: Boolean = false, depth: Boolean = true, lighting: Boolean = false, texture: Boolean = true, block: () -> Unit)
    fun drawTexturedQuad(x: Double, y: Double, z: Double, width: Double, height: Double, ...)
    fun renderEntity(entity: EntityLivingBase, x: Double, y: Double, z: Double, yaw: Float = 0f, scale: Float = 1f)
}
```

---

## 五、功能注册系统

### 功能注册表

```kotlin
object FeatureRegistry {
    fun register(feature: FeatureBase)
    fun get(id: String): FeatureBase?
    inline fun <reified T : FeatureBase> get(): T?
    fun getAll(): Collection<FeatureBase>
    fun enableAll()
    fun disableAll()
    fun registerPacketHandlers()
    fun registerEventHandlers()
}
```

### 功能扫描器

```kotlin
object FeatureScanner {
    fun scanAndRegister()  // 扫描 @Feature 注解，拓扑排序，依赖注入
}
```

### 服务定位器

```kotlin
object ServiceLocator {
    inline fun <reified T : Any> register(instance: T)
    inline fun <reified T : Any> get(): T?
    inline fun <reified T : Any> require(): T
}
```

---

## 六、功能模块

### Effect 模块（Ghost/Flicker/EntityShow）

```kotlin
@Feature("effect", description = "实体视觉效果")
object EffectFeature : FeatureBase() {
    @OnPacket(OrryxPacket.GhostEffect::class)
    fun onGhostPacket(packet: OrryxPacket.GhostEffect)

    @OnPacket(OrryxPacket.FlickerEffect::class)
    fun onFlickerPacket(packet: OrryxPacket.FlickerEffect)

    @OnPacket(OrryxPacket.EntityShowAdd::class)
    fun onEntityShowAdd(packet: OrryxPacket.EntityShowAdd)

    fun applyGhost(uuid: UUID, config: GhostConfig)
    fun applyFlicker(uuid: UUID, config: FlickerConfig)
    fun addShadow(uuid: UUID, group: String, position: Vector3d, rotation: EntityRotation, config: EntityShowConfig)
}
```

### Aim 模块

```kotlin
@Feature("aim", description = "技能辅助瞄准")
object AimFeature : FeatureBase() {
    @OnPacket(OrryxPacket.AimRequest::class)
    fun onAimRequest(packet: OrryxPacket.AimRequest)

    @OnPacket(OrryxPacket.AimConfirm::class)
    fun onAimConfirm(packet: OrryxPacket.AimConfirm)

    fun startAiming(skill: String, config: AimConfig = AimConfig())
    fun confirm()
    fun cancel()
}
```

### Shockwave 模块（DSL 风格）

```kotlin
// 使用示例
shockwave(world) {
    shape = circle {
        center(x, y, z)
        radius = 10.0
    }
    fracture {
        bounceMultiplier = 0.5
        lifetime = 150
    }
    particles {
        enabled = true
        density = 8
    }
}

@Feature("shockwave", description = "地面冲击波")
object ShockwaveFeature : FeatureBase() {
    @OnPacket(OrryxPacket.CircleShockwave::class)
    fun onCircle(packet: OrryxPacket.CircleShockwave)

    @OnPacket(OrryxPacket.SquareShockwave::class)
    fun onSquare(packet: OrryxPacket.SquareShockwave)

    @OnPacket(OrryxPacket.SectorShockwave::class)
    fun onSector(packet: OrryxPacket.SectorShockwave)
}
```

---

## 七、文件结构

```
io.github.orryxmod/
├── OrryxMod.kt
├── OrryxCoreMod.kt
├── core/
│   ├── api/
│   │   ├── Annotations.kt
│   │   ├── FeatureBase.kt
│   │   └── RenderableEffect.kt
│   ├── event/
│   │   ├── EventBus.kt
│   │   └── Events.kt
│   ├── network/
│   │   ├── OrryxPacket.kt
│   │   ├── PacketCodec.kt
│   │   ├── PacketDispatcher.kt
│   │   └── NetworkHandler.kt
│   ├── render/
│   │   ├── RenderContext.kt
│   │   ├── EffectManager.kt
│   │   ├── RenderUtils.kt
│   │   └── WorldRenderHandler.kt
│   ├── registry/
│   │   ├── FeatureRegistry.kt
│   │   ├── FeatureScanner.kt
│   │   └── ServiceLocator.kt
│   └── handler/
│       ├── CommandHandler.kt
│       └── DisconnectHandler.kt
├── feature/
│   ├── aim/
│   ├── effect/
│   ├── shockwave/
│   ├── navigation/
│   └── mouse/
├── shared/
│   ├── texture/
│   └── math/
└── util/
```

---

## 八、实现计划

| 阶段 | 模块 | 预估行数 |
|------|------|---------|
| 1 | core/api/ | ~150 |
| 2 | core/event/ | ~100 |
| 3 | core/network/ | ~300 |
| 4 | core/render/ | ~250 |
| 5 | core/registry/ | ~250 |
| 6 | core/handler/ | ~100 |
| 7 | shared/ | ~150 |
| 8 | util/ | ~50 |
| 9 | feature/effect/ | ~350 |
| 10 | feature/aim/ | ~300 |
| 11 | feature/shockwave/ | ~700 |
| 12 | feature/navigation/ | ~150 |
| 13 | feature/mouse/ | ~50 |
| 14 | OrryxMod.kt | ~100 |
| **总计** | | **~3000 行** |

---

## 九、对比

| 指标 | 现有 | 重构后 |
|------|------|--------|
| 总代码行数 | ~2000 | ~3000 |
| 文件数量 | 24 | ~50 |
| 代码重复 | 高 | 极低 |
| 可扩展性 | 差 | 优秀 |
| 可测试性 | 差 | 良好 |
| 职责分离 | 混乱 | 清晰 |
