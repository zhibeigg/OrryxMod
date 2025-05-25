package io.github.orryxmod.util

import net.minecraft.client.Minecraft
import kotlin.math.sin
import kotlin.math.sqrt

val MC: Minecraft = Minecraft.getMinecraft()

class Vector3f(val x: Float, val y: Float, val z: Float)

fun Vector3f.rotateY(angle: Float): Vector3f {
    val sin = sin(angle)
    val cos: Float = cosFromSin(sin, angle)
    val x: Float = this.x * cos + this.z * sin
    val z: Float = -this.x * sin + this.z * cos
    return Vector3f(x, this.y, z)
}

fun cosFromSin(sin: Float, angle: Float): Float {
    val cos = sqrt(1.0f - sin * sin)
    val a: Float = angle + PI_OVER_2_f
    var b: Float = a - (a / PI_TIMES_2_f).toInt() * PI_TIMES_2_f
    if (b < 0.0) b = PI_TIMES_2_f + b
    if (b >= PI_f) return -cos
    return cos
}

const val PI = Math.PI
const val PI_TIMES_2 = PI * 2.0
const val PI_f = Math.PI.toFloat()
const val PI_TIMES_2_f = PI_f * 2.0f
const val PI_OVER_2 = Math.PI * 0.5
const val PI_OVER_2_f = (Math.PI * 0.5).toFloat()