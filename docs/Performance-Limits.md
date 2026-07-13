# OrryxMod 性能上限

本文档记录 1.4.5 的客户端性能保护默认值。所有世界、实体和 OpenGL 操作仍在 Minecraft 客户端主线程/渲染线程执行；这些配置用于限流，不会把线程敏感逻辑移到异步线程。

## Shockwave

运行时入口：`ShockwaveFeature.performanceConfig`；自定义 DSL 也可使用 `performance { ... }`。

| 配置 | 默认值 | 硬上限 | 说明 |
|---|---:|---:|---|
| `maxQueuedTasks` | 8 | 32 | 等待处理的冲击波任务数 |
| `maxPropagationNodes` | 8192 | 65536 | 单次冲击波最多处理的地面节点 |
| `maxFractureBlocks` | 256 | 1024 | 单次冲击波最多生成的断裂方块 |
| `maxActiveFractureBlocks` | 512 | 2048 | 客户端同时存在的断裂方块上限 |
| `maxParticles` | 1024 | 4096 | 单次冲击波粒子总数 |
| `propagationNodesPerTick` | 512 | 2048 | 每 tick 处理的传播节点 |
| `fractureBlocksPerTick` | 24 | 64 | 每 tick 生成的断裂方块 |
| `particlesPerTick` | 96 | 256 | 每 tick 生成的粒子 |

传播使用按距离从近到远的 Bresenham 游标，任务在 `ClientTick.END` 分批执行。达到任一总量上限后会安全结束当前效果。

```kotlin
ShockwaveFeature.performanceConfig = ShockwavePerformanceConfig(
    maxFractureBlocks = 128,
    fractureBlocksPerTick = 12
)
```

## Bloom

运行时入口：`BloomFeature.Config`。

| 配置 | 默认值 | 硬上限 | 说明 |
|---|---:|---:|---|
| `candidateRefreshTicks` | 5 | 200 | 候选实体缓存刷新间隔 |
| `maxCandidateScanPerRefresh` | 2048 | 4096 | 单次刷新最多扫描的实体数 |
| `maxCandidateEntities` | 256 | 1024 | 候选缓存最大实体数 |
| `maxMatchCacheEntries` | 2048 | 8192 | 实体名称匹配缓存项数 |
| `maxBloomEntities` | 32 | 256 | 每帧最多渲染的泛光实体数 |
| `maxBloomGroups` | 8 | 32 | 每帧最多执行的 Bloom 配置组数 |

配置组采用带权跨帧轮转：高优先级组获得更多剩余配额，但低优先级组仍有保底名额，不会永久饥饿。

## Collider

运行时入口：`ColliderFeature.renderConfig`。

| 距离 | 圆形细分 | Ray 箭头 | Composite 节点预算 |
|---|---:|---:|---:|
| 0–24 | 32 | 3 轴 | 128 |
| 24–64 | 24 | 2 轴 | 96 |
| 64–128 | 16 | 1 轴 | 64 |
| 128–192 | 8 | 仅主线 | 32 |
| >192 | 剔除 | 剔除 | 剔除 |

其他默认值：低质量 Composite 抽样步长 2、最低质量步长 4、最大递归深度 8。实现硬限制为圆形细分 8–64、Composite 预算最多 4096、包围范围缓存最多 512 项、包围范围递归最多 16 层。

```kotlin
ColliderFeature.renderConfig = ColliderRenderConfig(
    maxRenderDistance = 128.0,
    lowSegments = 12,
    minimumSegments = 8
)
```

## EntityTracker / Ghost / Flicker

运行时入口：`EffectFeature.Config`；底层也提供 `EntityTrackerRegistry.configureLimits(...)`。

| 配置 | 默认值 | 硬上限 | 说明 |
|---|---:|---:|---|
| `maxTrackerEntries` | 64 | 256 | 同时追踪的实体数量 |
| `maxTrackerSamplesPerEntity` | 64 | 256 | 单实体环形快照数量 |
| `maxFlickerEffects` | 20 | 64 | 同时存在的 Flicker 效果 |
| `maxCachedFlickerGeometries` | 24 | 64 | Display List 几何缓存项数 |
| `flickerGeometryTtlMillis` | 5000 ms | 60000 ms | 无引用几何的保留时间 |

Ghost 直接读取环形缓冲区的只读索引视图，不再每次渲染复制快照。样本不足时按当前可用数量降级渲染。Flicker 仅在玩家、模型实例和姿态帧完全相同时复用 Display List，并使用引用计数、容量淘汰、TTL 和断线清理避免资源泄漏。

```kotlin
EffectFeature.Config.maxTrackerEntries = 48
EffectFeature.Config.maxTrackerSamplesPerEntity = 48
EffectFeature.Config.maxCachedFlickerGeometries = 16
```
