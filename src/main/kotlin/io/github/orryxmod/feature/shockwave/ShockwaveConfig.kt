package io.github.orryxmod.feature.shockwave

/**
 * 断裂配置
 */
data class FractureConfig(
    val bounceMultiplier: Double = 0.1,
    val baseLifetime: Int = 200,
    val lifetimeVariance: Int = 30,
    val rotation: RotationConfig = RotationConfig()
)

/**
 * 旋转配置
 */
data class RotationConfig(
    val baseTilt: Float = 15f,
    val tiltVariance: Float = 5f,
    val yawVariance: Float = 20f,
    val rollVariance: Float = 7.5f
)

/**
 * 粒子配置
 */
data class ParticleConfig(
    val enabled: Boolean = true,
    val density: Int = 8,
    val velocityMultiplier: Float = 0.5f
)

/**
 * 冲击波执行预算。所有限制均按单次冲击波计算，tick 预算用于平摊主线程工作。
 */
data class ShockwavePerformanceConfig(
    val maxQueuedTasks: Int = 8,
    val maxPropagationNodes: Int = 8192,
    val maxFractureBlocks: Int = 256,
    val maxActiveFractureBlocks: Int = 512,
    val maxParticles: Int = 1024,
    val propagationNodesPerTick: Int = 512,
    val fractureBlocksPerTick: Int = 24,
    val particlesPerTick: Int = 96
)

/**
 * 冲击波完整配置
 */
data class ShockwaveConfig(
    val shape: Shape,
    val fracture: FractureConfig = FractureConfig(),
    val particles: ParticleConfig = ParticleConfig(),
    val performance: ShockwavePerformanceConfig = ShockwavePerformanceConfig()
)
