package io.github.orryxmod.feature.collider

/**
 * 碰撞体类型枚举
 */
enum class ColliderType(val wireId: Int) {
    SPHERE(0),
    AABB(1),
    OBB(2),
    CAPSULE(3),
    RAY(4),
    COMPOSITE(5),
    ORIENTED_CAPSULE(6);

    companion object {
        private val typesByWireId: Map<Int, ColliderType> = values().associateBy { it.wireId }

        init {
            require(typesByWireId.size == values().size) { "Collider wire IDs must be unique" }
        }

        fun fromWireId(wireId: Int): ColliderType? = typesByWireId[wireId]
    }
}

/**
 * 碰撞体几何数据密封类
 */
sealed class ColliderShape {

    /** 球体: 中心点 + 半径 */
    data class Sphere(
        val cx: Double, val cy: Double, val cz: Double,
        val radius: Double
    ) : ColliderShape()

    /** 轴对齐包围盒: 中心点 + 半尺寸 */
    data class AABB(
        val cx: Double, val cy: Double, val cz: Double,
        val hx: Double, val hy: Double, val hz: Double
    ) : ColliderShape()

    /** 有向包围盒: 中心点 + 半尺寸 + 四元数旋转 */
    data class OBB(
        val cx: Double, val cy: Double, val cz: Double,
        val hx: Double, val hy: Double, val hz: Double,
        val qx: Float, val qy: Float, val qz: Float, val qw: Float
    ) : ColliderShape()

    /** 胶囊体: 中心点 + 半径 + 半高 */
    data class Capsule(
        val cx: Double, val cy: Double, val cz: Double,
        val radius: Double, val halfHeight: Double
    ) : ColliderShape()

    /** 任意朝向胶囊体: 中心点 + 半径 + 半高 + 四元数旋转 */
    data class OrientedCapsule(
        val cx: Double, val cy: Double, val cz: Double,
        val radius: Double, val halfHeight: Double,
        val qx: Float, val qy: Float, val qz: Float, val qw: Float
    ) : ColliderShape()

    /** 射线: 起点 + 方向 + 长度 */
    data class Ray(
        val ox: Double, val oy: Double, val oz: Double,
        val dx: Double, val dy: Double, val dz: Double,
        val length: Double
    ) : ColliderShape()

    /** 复合碰撞体: 子碰撞体列表 */
    data class Composite(
        val children: List<ColliderData>
    ) : ColliderShape()
}

/**
 * 完整碰撞箱数据
 */
data class ColliderData(
    val id: String,
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int,
    val shape: ColliderShape
)
