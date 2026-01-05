# Shockwave 模块重构设计

## 概述

全面重构 Shockwave 模块，采用 Kotlin DSL 风格 API，实现可扩展性、可配置性、代码清晰度和性能优化四大目标。

## 设计目标

1. **可扩展性** — 方便添加新波形类型（锥形、螺旋形等）
2. **可配置性** — 波形参数（弹跳、旋转、粒子密度等）易于调整
3. **代码清晰度** — 消除重复，消除魔法数字，职责分离
4. **性能优化** — 延迟求值，方块缓存，分块处理

## API 设计

### DSL 使用示例

```kotlin
shockwave(world) {
    shape = circle {
        center(x, y, z)
        radius = 10.0
    }

    fracture {
        bounceMultiplier = 0.5
        lifetime = 150
        rotation { randomTilt(15f) }
    }

    particles {
        enabled = true
        density = 8
    }
}
```

### 形状类型

- `circle { }` — 圆形冲击波
- `square { }` — 方形冲击波
- `sector { }` — 扇形冲击波
- 可扩展更多形状

## 架构设计

### 核心数据模型

```kotlin
sealed interface Shape {
    val center: Vector3d
    fun spreadDirections(): Sequence<SpreadDirection>
}

data class SpreadDirection(
    val origin: Vector3d,
    val direction: Vector3d,
    val length: Double
)

data class CircleShape(override val center: Vector3d, val radius: Double) : Shape
data class SquareShape(override val center: Vector3d, val length: Double, val width: Double, val yaw: Double) : Shape
data class SectorShape(override val center: Vector3d, val radius: Double, val angle: Double, val yaw: Double) : Shape
```

### 配置数据类

```kotlin
data class FractureConfig(
    val bounceMultiplier: Double = 0.1,
    val baseLifetime: Int = 200,
    val lifetimeVariance: Int = 30,
    val rotation: RotationConfig = RotationConfig()
)

data class RotationConfig(
    val baseTilt: Float = 15f,
    val tiltVariance: Float = 5f,
    val yawVariance: Float = 20f,
    val rollVariance: Float = 7.5f
)

data class ParticleConfig(
    val enabled: Boolean = true,
    val density: Int = 8,
    val velocityMultiplier: Float = 0.5f
)

data class ShockwaveConfig(
    val world: World,
    val shape: Shape,
    val fracture: FractureConfig = FractureConfig(),
    val particles: ParticleConfig = ParticleConfig()
)
```

### 执行引擎

```kotlin
object ShockwaveExecutor {
    fun execute(config: ShockwaveConfig): Boolean {
        // 1. 验证起点
        // 2. 管道处理：收集 -> 去重 -> 排序 -> 处理
        config.shape.spreadDirections()
            .flatMap { collectBlocks(world, center, it) }
            .distinctBy { it.pos }
            .sortedBy { it.distance }
            .forEach { processBlock(config, it) }
    }
}
```

## 文件结构

```
fractureblock/
├── Shockwave.kt                    # Module 入口 + PacketHandler 兼容
├── dsl/
│   ├── ShockwaveDsl.kt            # 顶层 DSL
│   ├── ShapeBuilders.kt           # 形状 DSL
│   ├── FractureDsl.kt             # 断裂配置 DSL
│   └── ParticleDsl.kt             # 粒子配置 DSL
├── shape/
│   ├── Shape.kt                   # 密封接口
│   ├── CircleShape.kt
│   ├── SquareShape.kt
│   ├── SectorShape.kt
│   └── ShapeValidator.kt
├── config/
│   ├── ShockwaveConfig.kt
│   ├── FractureConfig.kt
│   ├── RotationConfig.kt
│   └── ParticleConfig.kt
├── executor/
│   ├── ShockwaveExecutor.kt
│   ├── BlockCollector.kt
│   ├── HeightResolver.kt
│   ├── FractureApplier.kt
│   └── ParticleSpawner.kt
└── util/
    ├── BlockCache.kt
    └── VectorExtensions.kt
```

## PacketHandler 兼容

保留现有函数签名，内部委托给 DSL：

```kotlin
object Shockwave : Module("Shockwave", "地面冲击波") {
    fun circleSlamFracture(x: Double, y: Double, z: Double, radius: Double): Boolean {
        val world = MC.world ?: return false
        return shockwave(world) {
            shape = circle {
                center(x, y, z)
                this.radius = radius
            }
        }
    }
    // squareSlamFracture, sectorSlamFracture 类似...
}
```

## 扩展新形状

添加新形状只需 3 步：

1. 定义数据类实现 `Shape` 接口
2. 实现 `spreadDirections()` 方法
3. 添加 DSL 构建函数

```kotlin
data class ConeShape(...) : Shape {
    override fun spreadDirections(): Sequence<SpreadDirection> = sequence { ... }
}

fun cone(block: ConeDsl.() -> Unit): ConeShape = ConeDsl().apply(block).build()
```

## 错误处理

- DSL 构建时验证必填项和参数范围
- 快速失败，抛出 `ShockwaveConfigException`
- 边界限制与 `PacketHandler` 的 `coerceIn` 保持一致

## 性能优化

1. **Sequence 延迟求值** — 大范围形状内存友好
2. **方块状态缓存** — 避免重复查询 `getBlockState()`
3. **分块处理** — 超大范围不会一次性处理所有方块

## 代码量估算

| 模块 | 预估行数 |
|------|---------|
| DSL 构建器 | ~150 行 |
| 形状定义 | ~200 行 |
| 配置数据类 | ~80 行 |
| 执行引擎 | ~250 行 |
| 工具类 | ~50 行 |
| **总计** | **~730 行** |

## 实现顺序

1. 配置数据类 (`config/`)
2. 形状定义 (`shape/`)
3. DSL 构建器 (`dsl/`)
4. 执行引擎 (`executor/`)
5. 入口和兼容层 (`Shockwave.kt`)
6. 删除旧代码，测试验证
