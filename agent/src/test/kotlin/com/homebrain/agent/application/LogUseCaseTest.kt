package com.homebrain.agent.application

import com.homebrain.agent.infrastructure.engine.EngineClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LogUseCaseTest {

    private lateinit var engineClient: EngineClient
    private lateinit var useCase: LogUseCase

    @BeforeEach
    fun setUp() {
        engineClient = mockk()
        useCase = LogUseCase(engineClient)
    }

    @Nested
    inner class GetLogs {
        @Test
        fun `should return logs from engine`() {
            val logs = listOf(
                mapOf("timestamp" to "2024-01-01T00:00:00Z", "automation" to "motion_light", "message" to "Motion detected"),
                mapOf("timestamp" to "2024-01-01T00:00:01Z", "automation" to "motion_light", "message" to "Light turned on")
            )
            every { engineClient.getLogs() } returns logs

            val result = useCase.getLogs()

            assertEquals(2, result.size)
            assertEquals("motion_light", result[0]["automation"])
            assertEquals("Motion detected", result[0]["message"])
            verify { engineClient.getLogs() }
        }

        @Test
        fun `should return empty list when no logs`() {
            every { engineClient.getLogs() } returns emptyList()

            val result = useCase.getLogs()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should handle logs with various fields`() {
            val logs = listOf(
                mapOf(
                    "timestamp" to "2024-01-01T00:00:00Z",
                    "automation" to "test",
                    "message" to "Test message",
                    "level" to "INFO",
                    "extra" to "data"
                )
            )
            every { engineClient.getLogs() } returns logs

            val result = useCase.getLogs()

            assertEquals(1, result.size)
            assertEquals("INFO", result[0]["level"])
            assertEquals("data", result[0]["extra"])
        }
    }
}
