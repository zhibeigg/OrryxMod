package io.github.orryxmod.core.network

import com.google.common.io.ByteArrayDataOutput
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.feature.bloom.BloomConfig
import io.github.orryxmod.feature.collider.ColliderData
import io.github.orryxmod.feature.collider.ColliderShape
import io.github.orryxmod.feature.collider.ColliderType
import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.DataInputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 协议编解码器
 */
object PacketCodec {

    /** 协议版本号 */
    const val PROTOCOL_VERSION = 1

    /** 集合大小上限，防止恶意数据包 */
    private const val MAX_COLLECTION_SIZE = 1000

    /** 字符串长度上限 */
    private const val MAX_STRING_LENGTH = 1024

    /** Minecraft 有效世界坐标范围 */
    private const val MAX_WORLD_COORDINATE = 30_000_000.0

    private const val MIN_AIM_SCALE = 0.01
    private const val MAX_AIM_SCALE = 100.0
    private const val MIN_AIM_DISTANCE = 0.1
    private const val MAX_AIM_DISTANCE = 512.0

    /** COMPOSITE 单层子碰撞体上限 */
    private const val MAX_COMPOSITE_CHILDREN = 50

    /** COMPOSITE 递归深度上限 */
    private const val MAX_COMPOSITE_DEPTH = 3

    /** 单个 COMPOSITE 树允许的总节点数 */
    private const val MAX_COMPOSITE_TOTAL_NODES = 200

    private const val NORMALIZATION_EPSILON = 1.0e-12

    /**
     * 解码：完整字节数组 -> OrryxPacket。
     * 保留剩余字节信息，确保旧 Aim 包与被截断的新包可以可靠区分。
     */
    fun decode(bytes: ByteArray): OrryxPacket? {
        val byteStream = ByteArrayInputStream(bytes)
        val input = DataInputStream(byteStream)

        return try {
            val packet = when (val id = input.readInt()) {
                1 -> readAimRequest(input) { byteStream.available() }
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
                    alpha = input.readBoundedFloat("flicker alpha", 0f, 1f),
                    duration = input.readLong().coerceIn(-1, 60_000),
                    scale = input.readBoundedFloat("flicker scale", 0.1f, 10f)
                )
                7 -> OrryxPacket.MouseControl(
                    show = input.readBoolean()
                )
                8 -> OrryxPacket.EntityShowAdd(
                    uuid = input.readUUID(),
                    group = input.readSafeUTF(),
                    x = input.readWorldCoordinate("entity x"),
                    y = input.readWorldCoordinate("entity y"),
                    z = input.readWorldCoordinate("entity z"),
                    timeout = input.readLong().coerceIn(0, 300_000),
                    rotateX = input.readBoundedFloat("entity rotateX", -360f, 360f),
                    rotateY = input.readBoundedFloat("entity rotateY", -360f, 360f),
                    rotateZ = input.readBoundedFloat("entity rotateZ", -360f, 360f),
                    scale = input.readBoundedFloat("entity scale", 0.01f, 10f),
                    alpha = input.readBoundedFloat("entity alpha", 0f, 1f),
                    fadeOut = input.readBoolean()
                )
                9 -> OrryxPacket.EntityShowRemove(
                    uuid = input.readUUID(),
                    group = input.readSafeUTF()
                )
                10 -> OrryxPacket.NavigationStart(
                    x = input.readInt(),
                    y = input.readInt(),
                    z = input.readInt(),
                    range = input.readInt().coerceIn(0, 100)
                )
                11 -> OrryxPacket.NavigationStop
                12 -> OrryxPacket.SquareShockwave(
                    x = input.readWorldCoordinate("square shockwave x"),
                    y = input.readWorldCoordinate("square shockwave y"),
                    z = input.readWorldCoordinate("square shockwave z"),
                    length = input.readBoundedDouble("square shockwave length", 0.5, 100.0),
                    width = input.readBoundedDouble("square shockwave width", 0.5, 100.0),
                    yaw = input.readWrappedDegrees("square shockwave yaw")
                )
                13 -> OrryxPacket.CircleShockwave(
                    x = input.readWorldCoordinate("circle shockwave x"),
                    y = input.readWorldCoordinate("circle shockwave y"),
                    z = input.readWorldCoordinate("circle shockwave z"),
                    radius = input.readBoundedDouble("circle shockwave radius", 0.5, 100.0)
                )
                14 -> OrryxPacket.SectorShockwave(
                    x = input.readWorldCoordinate("sector shockwave x"),
                    y = input.readWorldCoordinate("sector shockwave y"),
                    z = input.readWorldCoordinate("sector shockwave z"),
                    radius = input.readBoundedDouble("sector shockwave radius", 0.5, 100.0),
                    angle = input.readBoundedDouble("sector shockwave angle", 0.0, 360.0),
                    yaw = input.readWrappedDegrees("sector shockwave yaw")
                )
                15 -> {
                    val count = input.readCollectionSize("bloom config count", MAX_COLLECTION_SIZE)
                    val configs = mutableMapOf<String, BloomConfig>()
                    repeat(count) {
                        val configId = input.readSafeUTF()
                        configs[configId] = readBloomConfig(input)
                    }
                    OrryxPacket.BloomConfigSync(configs)
                }
                16 -> OrryxPacket.BloomConfigUpdate(
                    id = input.readSafeUTF(),
                    config = readBloomConfig(input)
                )
                17 -> OrryxPacket.BloomConfigRemove(
                    id = input.readSafeUTF()
                )
                18 -> {
                    val colliderId = input.readSafeUTF()
                    val type = input.readColliderType("collider")
                    val r = input.readInt().coerceIn(0, 255)
                    val g = input.readInt().coerceIn(0, 255)
                    val b = input.readInt().coerceIn(0, 255)
                    val a = input.readInt().coerceIn(0, 255)
                    val shape = readColliderShape(input, type)
                    OrryxPacket.ColliderShow(colliderId, r, g, b, a, shape)
                }
                19 -> {
                    val colliderId = input.readSafeUTF()
                    val type = input.readColliderType("collider")
                    val shape = readColliderShape(input, type)
                    OrryxPacket.ColliderUpdate(colliderId, shape)
                }
                20 -> OrryxPacket.ColliderRemove(
                    id = input.readSafeUTF()
                )
                else -> {
                    OrryxMod.logger.warn("Unknown packet ID: $id")
                    null
                }
            }

            if (packet != null) {
                require(byteStream.available() == 0) {
                    "Packet contains ${byteStream.available()} unexpected trailing bytes"
                }
            }
            packet
        } catch (ex: Exception) {
            OrryxMod.logger.error("Error decoding packet", ex)
            null
        }
    }

    /**
     * 编码：OrryxPacket -> 字节流
     */
    fun encode(packet: OrryxPacket, output: ByteArrayDataOutput) {
        val response = packet as? OrryxPacket.AimResponse
            ?: throw IllegalArgumentException("Only AimResponse packets can be encoded by the client")

        require(response.skill.length <= MAX_STRING_LENGTH) {
            "String too long: ${response.skill.length} > $MAX_STRING_LENGTH"
        }

        val x = response.x.requireFinite("aim response x").coerceIn(-MAX_WORLD_COORDINATE, MAX_WORLD_COORDINATE)
        val y = response.y.requireFinite("aim response y").coerceIn(-MAX_WORLD_COORDINATE, MAX_WORLD_COORDINATE)
        val z = response.z.requireFinite("aim response z").coerceIn(-MAX_WORLD_COORDINATE, MAX_WORLD_COORDINATE)
        val yaw = response.yaw.wrapDegrees("aim response yaw")
        val pitch = response.pitch.requireFinite("aim response pitch").coerceIn(-90f, 90f)

        output.writeInt(response.packetId)
        output.writeUTF(response.skill)
        output.writeDouble(x)
        output.writeDouble(y)
        output.writeDouble(z)
        output.writeFloat(yaw)
        output.writeFloat(pitch)
    }

    private fun readAimRequest(input: DataInput, remainingBytes: () -> Int): OrryxPacket.AimRequest {
        val skill = input.readSafeUTF()
        val module = input.readSafeUTF()
        val scale = input.readBoundedDouble("aim scale", MIN_AIM_SCALE, MAX_AIM_SCALE)
        val maxDistance = input.readBoundedDouble("aim maxDistance", MIN_AIM_DISTANCE, MAX_AIM_DISTANCE)

        // 旧服务端只发送到 maxDistance。只在此处恰好没有剩余字节时补默认值；
        // 只要扩展字段已经开始，就必须完整解码，截断包会被外层拒绝。
        if (remainingBytes() == 0) {
            return OrryxPacket.AimRequest(
                skill = skill,
                module = module,
                scale = scale,
                maxDistance = maxDistance
            )
        }

        return OrryxPacket.AimRequest(
            skill = skill,
            module = module,
            scale = scale,
            maxDistance = maxDistance,
            indicatorType = input.readSafeUTF(),
            indicatorColor = input.readInt(),
            indicatorAlpha = input.readBoundedFloat("aim indicatorAlpha", 0f, 1f),
            indicatorRadius = input.readBoundedDouble("aim indicatorRadius", 0.1, 50.0),
            modelScale = input.readBoundedFloat("aim modelScale", 0.1f, 10f)
        )
    }

    /**
     * 从输入流读取 UUID
     */
    private fun DataInput.readUUID(): UUID {
        val str = readSafeUTF()
        return runCatching { UUID.fromString(str) }.getOrElse {
            throw IllegalArgumentException("Invalid UUID format: $str")
        }
    }

    /**
     * 安全读取字符串，限制长度防止内存耗尽
     */
    private fun DataInput.readSafeUTF(): String {
        val str = readUTF()
        if (str.length > MAX_STRING_LENGTH) {
            throw IllegalArgumentException("String too long: ${str.length} > $MAX_STRING_LENGTH")
        }
        return str
    }

    private fun DataInput.readCollectionSize(field: String, maximum: Int): Int {
        val size = readInt()
        require(size in 0..maximum) { "$field out of range: $size (maximum $maximum)" }
        return size
    }

    private fun DataInput.readFiniteDouble(field: String): Double = readDouble().requireFinite(field)

    private fun DataInput.readFiniteFloat(field: String): Float = readFloat().requireFinite(field)

    private fun DataInput.readBoundedDouble(field: String, minimum: Double, maximum: Double): Double {
        return readFiniteDouble(field).coerceIn(minimum, maximum)
    }

    private fun DataInput.readBoundedFloat(field: String, minimum: Float, maximum: Float): Float {
        return readFiniteFloat(field).coerceIn(minimum, maximum)
    }

    private fun DataInput.readWorldCoordinate(field: String): Double {
        return readBoundedDouble(field, -MAX_WORLD_COORDINATE, MAX_WORLD_COORDINATE)
    }

    private fun DataInput.readWrappedDegrees(field: String): Double {
        return readFiniteDouble(field).wrapDegrees(field)
    }

    private fun Double.wrapDegrees(field: String): Double {
        val wrapped = requireFinite(field) % 360.0
        return when {
            wrapped >= 180.0 -> wrapped - 360.0
            wrapped < -180.0 -> wrapped + 360.0
            else -> wrapped
        }
    }

    private fun Float.wrapDegrees(field: String): Float {
        val wrapped = requireFinite(field) % 360f
        return when {
            wrapped >= 180f -> wrapped - 360f
            wrapped < -180f -> wrapped + 360f
            else -> wrapped
        }
    }

    private fun Double.requireFinite(field: String): Double {
        require(isFinite()) { "$field must be finite" }
        return this
    }

    private fun Float.requireFinite(field: String): Float {
        require(isFinite()) { "$field must be finite" }
        return this
    }

    /**
     * 从输入流读取 BloomConfig
     */
    private fun readBloomConfig(input: DataInput): BloomConfig {
        return BloomConfig(
            name = input.readSafeUTF(),
            color = intArrayOf(
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255)
            ),
            strength = input.readBoundedFloat("bloom strength", 0f, 10f),
            radius = input.readBoundedFloat("bloom radius", 1f, 128f),
            priority = input.readInt()
        )
    }

    private fun DataInput.readColliderType(context: String): ColliderType {
        val wireId = readInt()
        return ColliderType.fromWireId(wireId)
            ?: throw IllegalArgumentException("Unknown $context type wire ID: $wireId")
    }

    /**
     * 从输入流读取碰撞体几何数据
     */
    private fun readColliderShape(
        input: DataInput,
        type: ColliderType,
        depth: Int = 0,
        budget: ColliderNodeBudget = ColliderNodeBudget()
    ): ColliderShape {
        budget.consume()

        return when (type) {
            ColliderType.SPHERE -> ColliderShape.Sphere(
                cx = input.readWorldCoordinate("sphere cx"),
                cy = input.readWorldCoordinate("sphere cy"),
                cz = input.readWorldCoordinate("sphere cz"),
                radius = input.readBoundedDouble("sphere radius", 0.01, 100.0)
            )
            ColliderType.AABB -> ColliderShape.AABB(
                cx = input.readWorldCoordinate("AABB cx"),
                cy = input.readWorldCoordinate("AABB cy"),
                cz = input.readWorldCoordinate("AABB cz"),
                hx = input.readBoundedDouble("AABB hx", 0.01, 100.0),
                hy = input.readBoundedDouble("AABB hy", 0.01, 100.0),
                hz = input.readBoundedDouble("AABB hz", 0.01, 100.0)
            )
            ColliderType.OBB -> readObb(input)
            ColliderType.CAPSULE -> ColliderShape.Capsule(
                cx = input.readWorldCoordinate("capsule cx"),
                cy = input.readWorldCoordinate("capsule cy"),
                cz = input.readWorldCoordinate("capsule cz"),
                radius = input.readBoundedDouble("capsule radius", 0.01, 100.0),
                halfHeight = input.readBoundedDouble("capsule halfHeight", 0.01, 100.0)
            )
            ColliderType.RAY -> readRay(input)
            ColliderType.COMPOSITE -> {
                if (depth >= MAX_COMPOSITE_DEPTH) {
                    throw IllegalArgumentException("Composite depth exceeds limit: $MAX_COMPOSITE_DEPTH")
                }
                val count = input.readCollectionSize("composite child count", MAX_COMPOSITE_CHILDREN)
                val children = ArrayList<ColliderData>(count)
                repeat(count) {
                    val childId = input.readSafeUTF()
                    val childType = input.readColliderType("child collider")
                    val r = input.readInt().coerceIn(0, 255)
                    val g = input.readInt().coerceIn(0, 255)
                    val b = input.readInt().coerceIn(0, 255)
                    val a = input.readInt().coerceIn(0, 255)
                    val childShape = readColliderShape(input, childType, depth + 1, budget)
                    children.add(ColliderData(childId, r, g, b, a, childShape))
                }
                ColliderShape.Composite(children)
            }
        }
    }

    private fun readObb(input: DataInput): ColliderShape.OBB {
        val cx = input.readWorldCoordinate("OBB cx")
        val cy = input.readWorldCoordinate("OBB cy")
        val cz = input.readWorldCoordinate("OBB cz")
        val hx = input.readBoundedDouble("OBB hx", 0.01, 100.0)
        val hy = input.readBoundedDouble("OBB hy", 0.01, 100.0)
        val hz = input.readBoundedDouble("OBB hz", 0.01, 100.0)
        val quaternion = normalizeQuaternion(
            input.readFiniteFloat("OBB qx"),
            input.readFiniteFloat("OBB qy"),
            input.readFiniteFloat("OBB qz"),
            input.readFiniteFloat("OBB qw")
        )

        return ColliderShape.OBB(
            cx = cx,
            cy = cy,
            cz = cz,
            hx = hx,
            hy = hy,
            hz = hz,
            qx = quaternion[0],
            qy = quaternion[1],
            qz = quaternion[2],
            qw = quaternion[3]
        )
    }

    private fun readRay(input: DataInput): ColliderShape.Ray {
        val ox = input.readWorldCoordinate("ray ox")
        val oy = input.readWorldCoordinate("ray oy")
        val oz = input.readWorldCoordinate("ray oz")
        val direction = normalizeVector(
            input.readFiniteDouble("ray dx"),
            input.readFiniteDouble("ray dy"),
            input.readFiniteDouble("ray dz")
        )
        val length = input.readBoundedDouble("ray length", 0.01, 200.0)

        return ColliderShape.Ray(
            ox = ox,
            oy = oy,
            oz = oz,
            dx = direction[0],
            dy = direction[1],
            dz = direction[2],
            length = length
        )
    }

    private fun normalizeVector(x: Double, y: Double, z: Double): DoubleArray {
        val maximum = maxOf(abs(x), abs(y), abs(z))
        require(maximum > NORMALIZATION_EPSILON) { "Ray direction must not be a zero vector" }

        val scaledX = x / maximum
        val scaledY = y / maximum
        val scaledZ = z / maximum
        val magnitude = sqrt(scaledX * scaledX + scaledY * scaledY + scaledZ * scaledZ)

        return doubleArrayOf(scaledX / magnitude, scaledY / magnitude, scaledZ / magnitude)
    }

    private fun normalizeQuaternion(x: Float, y: Float, z: Float, w: Float): FloatArray {
        val maximum = maxOf(abs(x), abs(y), abs(z), abs(w)).toDouble()
        require(maximum > NORMALIZATION_EPSILON) { "OBB quaternion must not be zero" }

        val scaledX = x / maximum
        val scaledY = y / maximum
        val scaledZ = z / maximum
        val scaledW = w / maximum
        val magnitude = sqrt(
            scaledX * scaledX + scaledY * scaledY + scaledZ * scaledZ + scaledW * scaledW
        )

        return floatArrayOf(
            (scaledX / magnitude).toFloat(),
            (scaledY / magnitude).toFloat(),
            (scaledZ / magnitude).toFloat(),
            (scaledW / magnitude).toFloat()
        )
    }

    private class ColliderNodeBudget {
        private var remaining = MAX_COMPOSITE_TOTAL_NODES

        fun consume() {
            if (remaining <= 0) {
                throw IllegalArgumentException(
                    "Composite node count exceeds limit: $MAX_COMPOSITE_TOTAL_NODES"
                )
            }
            remaining--
        }
    }
}
