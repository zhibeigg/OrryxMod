package io.github.orryxmod.feature.shockwave

import net.minecraft.world.World
import org.joml.Vector3d

/**
 * Shockwave DSL 入口函数
 */
fun shockwave(world: World, block: ShockwaveDsl.() -> Unit): Boolean {
    val dsl = ShockwaveDsl(world)
    dsl.block()
    return dsl.execute()
}

/**
 * Shockwave DSL 构建器
 */
class ShockwaveDsl(private val world: World) {

    var shape: Shape? = null

    private var fractureConfig = FractureConfig()
    private var particleConfig = ParticleConfig()

    /**
     * 配置断裂效果
     */
    fun fracture(block: FractureDsl.() -> Unit) {
        val dsl = FractureDsl()
        dsl.block()
        fractureConfig = dsl.build()
    }

    /**
     * 配置粒子效果
     */
    fun particles(block: ParticleDsl.() -> Unit) {
        val dsl = ParticleDsl()
        dsl.block()
        particleConfig = dsl.build()
    }

    /**
     * 执行冲击波
     */
    internal fun execute(): Boolean {
        val currentShape = shape ?: error("Shape must be specified")

        val config = ShockwaveConfig(
            shape = currentShape,
            fracture = fractureConfig,
            particles = particleConfig
        )

        return ShockwaveExecutor.execute(world, config)
    }
}

/**
 * 断裂配置 DSL
 */
class FractureDsl {
    var bounceMultiplier: Double = 0.1
    var lifetime: Int = 200
    var lifetimeVariance: Int = 30

    private var rotationConfig = RotationConfig()

    fun rotation(block: RotationDsl.() -> Unit) {
        val dsl = RotationDsl()
        dsl.block()
        rotationConfig = dsl.build()
    }

    internal fun build() = FractureConfig(
        bounceMultiplier = bounceMultiplier,
        baseLifetime = lifetime,
        lifetimeVariance = lifetimeVariance,
        rotation = rotationConfig
    )
}

/**
 * 旋转配置 DSL
 */
class RotationDsl {
    var baseTilt: Float = 15f
    var tiltVariance: Float = 5f
    var yawVariance: Float = 20f
    var rollVariance: Float = 7.5f

    fun randomTilt(degrees: Float) {
        baseTilt = degrees
        tiltVariance = degrees / 3
    }

    internal fun build() = RotationConfig(
        baseTilt = baseTilt,
        tiltVariance = tiltVariance,
        yawVariance = yawVariance,
        rollVariance = rollVariance
    )
}

/**
 * 粒子配置 DSL
 */
class ParticleDsl {
    var enabled: Boolean = true
    var density: Int = 8
    var velocityMultiplier: Float = 0.5f

    internal fun build() = ParticleConfig(
        enabled = enabled,
        density = density,
        velocityMultiplier = velocityMultiplier
    )
}

// ========== Shape DSL 构建函数 ==========

/**
 * 创建圆形形状
 */
fun circle(block: CircleDsl.() -> Unit): CircleShape {
    val dsl = CircleDsl()
    dsl.block()
    return dsl.build()
}

/**
 * 创建方形形状
 */
fun square(block: SquareDsl.() -> Unit): SquareShape {
    val dsl = SquareDsl()
    dsl.block()
    return dsl.build()
}

/**
 * 创建扇形形状
 */
fun sector(block: SectorDsl.() -> Unit): SectorShape {
    val dsl = SectorDsl()
    dsl.block()
    return dsl.build()
}

/**
 * 圆形 DSL
 */
class CircleDsl {
    private var x: Double = 0.0
    private var y: Double = 0.0
    private var z: Double = 0.0
    var radius: Double = 5.0

    fun center(x: Double, y: Double, z: Double) {
        this.x = x
        this.y = y
        this.z = z
    }

    internal fun build() = CircleShape(Vector3d(x, y, z), radius)
}

/**
 * 方形 DSL
 */
class SquareDsl {
    private var x: Double = 0.0
    private var y: Double = 0.0
    private var z: Double = 0.0
    var length: Double = 5.0
    var width: Double = 5.0
    var yaw: Double = 0.0

    fun center(x: Double, y: Double, z: Double) {
        this.x = x
        this.y = y
        this.z = z
    }

    internal fun build() = SquareShape(Vector3d(x, y, z), length, width, yaw)
}

/**
 * 扇形 DSL
 */
class SectorDsl {
    private var x: Double = 0.0
    private var y: Double = 0.0
    private var z: Double = 0.0
    var radius: Double = 5.0
    var angle: Double = 90.0
    var yaw: Double = 0.0

    fun center(x: Double, y: Double, z: Double) {
        this.x = x
        this.y = y
        this.z = z
    }

    internal fun build() = SectorShape(Vector3d(x, y, z), radius, angle, yaw)
}
