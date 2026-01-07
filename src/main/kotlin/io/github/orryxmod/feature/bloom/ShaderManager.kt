package io.github.orryxmod.feature.bloom

import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.shader.Framebuffer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 着色器管理器
 * 简化版实现，不依赖 CodeChickenLib
 */
@SideOnly(Side.CLIENT)
object ShaderManager {

    private val programs = mutableMapOf<String, Int>()
    private val uniformLocations = mutableMapOf<String, MutableMap<String, Int>>()

    // 着色器对象
    var IMAGE_V: Int = 0
        private set
    var IMAGE_F: Int = 0
        private set
    var BLUR: Int = 0
        private set
    var BLOOM_COMBINE: Int = 0
        private set

    // 程序对象
    var PROGRAM_IMAGE: Int = 0
        private set
    var PROGRAM_BLUR: Int = 0
        private set
    var PROGRAM_BLOOM_COMBINE: Int = 0
        private set

    private var initialized = false

    fun allowedShader(): Boolean = OpenGlHelper.shadersSupported

    fun init() {
        if (initialized || !allowedShader()) return

        try {
            // 加载着色器
            IMAGE_V = loadShader(GL20.GL_VERTEX_SHADER, "image.vert")
            IMAGE_F = loadShader(GL20.GL_FRAGMENT_SHADER, "image.frag")
            BLUR = loadShader(GL20.GL_FRAGMENT_SHADER, "blur.frag")
            BLOOM_COMBINE = loadShader(GL20.GL_FRAGMENT_SHADER, "bloom_combine.frag")

            // 创建程序
            PROGRAM_IMAGE = createProgram(IMAGE_V, IMAGE_F)
            PROGRAM_BLUR = createProgram(IMAGE_V, BLUR)
            PROGRAM_BLOOM_COMBINE = createProgram(IMAGE_V, BLOOM_COMBINE)

            initialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadShader(type: Int, filename: String): Int {
        val source = readShaderSource("/assets/orryxmod/shaders/$filename")
        val shader = GL20.glCreateShader(type)
        GL20.glShaderSource(shader, source)
        GL20.glCompileShader(shader)

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetShaderInfoLog(shader, 1024)
            throw RuntimeException("Shader compile error ($filename): $log")
        }

        return shader
    }

    private fun readShaderSource(path: String): String {
        val stream = ShaderManager::class.java.getResourceAsStream(path)
            ?: throw RuntimeException("Shader not found: $path")

        return BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.readText()
        }
    }

    private fun createProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GL20.glCreateProgram()
        GL20.glAttachShader(program, vertexShader)
        GL20.glAttachShader(program, fragmentShader)

        // 绑定属性位置
        GL20.glBindAttribLocation(program, 0, "position")

        GL20.glLinkProgram(program)

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetProgramInfoLog(program, 1024)
            throw RuntimeException("Program link error: $log")
        }

        return program
    }

    fun useProgram(program: Int) {
        GL20.glUseProgram(program)
    }

    fun releaseProgram() {
        GL20.glUseProgram(0)
    }

    fun getUniformLocation(program: Int, name: String): Int {
        val programName = program.toString()
        val cache = uniformLocations.getOrPut(programName) { mutableMapOf() }
        return cache.getOrPut(name) {
            GL20.glGetUniformLocation(program, name)
        }
    }

    fun setUniform1i(program: Int, name: String, value: Int) {
        GL20.glUniform1i(getUniformLocation(program, name), value)
    }

    fun setUniform1f(program: Int, name: String, value: Float) {
        GL20.glUniform1f(getUniformLocation(program, name), value)
    }

    fun setUniform2f(program: Int, name: String, x: Float, y: Float) {
        GL20.glUniform2f(getUniformLocation(program, name), x, y)
    }

    /**
     * 渲染全屏四边形到 FBO
     */
    fun renderFullImageInFBO(
        fbo: Framebuffer,
        program: Int,
        uniformSetup: ((Int) -> Unit)? = null
    ): Framebuffer {
        if (!allowedShader()) return fbo

        fbo.bindFramebuffer(true)

        useProgram(program)

        // 设置分辨率 uniform
        setUniform2f(program, "u_resolution", fbo.framebufferWidth.toFloat(), fbo.framebufferHeight.toFloat())

        // 自定义 uniform 设置
        uniformSetup?.invoke(program)

        // 渲染全屏四边形（使用立即模式）
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex2f(-1f, -1f)
        GL11.glVertex2f(1f, -1f)
        GL11.glVertex2f(1f, 1f)
        GL11.glVertex2f(-1f, 1f)
        GL11.glEnd()

        releaseProgram()

        return fbo
    }

    fun cleanup() {
        if (!initialized) return

        if (PROGRAM_IMAGE != 0) GL20.glDeleteProgram(PROGRAM_IMAGE)
        if (PROGRAM_BLUR != 0) GL20.glDeleteProgram(PROGRAM_BLUR)
        if (PROGRAM_BLOOM_COMBINE != 0) GL20.glDeleteProgram(PROGRAM_BLOOM_COMBINE)

        if (IMAGE_V != 0) GL20.glDeleteShader(IMAGE_V)
        if (IMAGE_F != 0) GL20.glDeleteShader(IMAGE_F)
        if (BLUR != 0) GL20.glDeleteShader(BLUR)
        if (BLOOM_COMBINE != 0) GL20.glDeleteShader(BLOOM_COMBINE)

        programs.clear()
        uniformLocations.clear()
        initialized = false
    }
}
