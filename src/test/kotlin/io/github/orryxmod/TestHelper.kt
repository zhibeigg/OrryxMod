package io.github.orryxmod

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.apache.logging.log4j.Logger
import io.mockk.mockk

/**
 * 测试辅助工具
 */
object TestHelper {

    /**
     * mock OrryxMod.logger，多个被测类依赖此静态字段
     */
    fun mockLogger(): Logger {
        val logger = mockk<Logger>(relaxed = true)
        mockkObject(OrryxMod.Companion)
        every { OrryxMod.logger } returns logger
        return logger
    }

    /**
     * 清理所有 mock
     */
    fun cleanup() {
        unmockkAll()
    }
}
