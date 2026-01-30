package com.homebrain.agent.application

import com.homebrain.agent.domain.library.LibraryModule
import com.homebrain.agent.infrastructure.engine.EngineClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LibraryUseCaseTest {

    private lateinit var engineClient: EngineClient
    private lateinit var useCase: LibraryUseCase

    @BeforeEach
    fun setUp() {
        engineClient = mockk()
        useCase = LibraryUseCase(engineClient)
    }

    @Nested
    inner class GetAllModules {
        @Test
        fun `should return all library modules from engine`() {
            val modules = listOf(
                LibraryModule.create("timers", "Timer utilities", listOf("debounce_check")),
                LibraryModule.create("utils", "Helper utilities", listOf("safe_get", "clamp"))
            )
            every { engineClient.getLibraryModules() } returns modules

            val result = useCase.getAllModules()

            assertEquals(2, result.size)
            assertEquals("timers", result[0].name)
            assertEquals("utils", result[1].name)
            verify { engineClient.getLibraryModules() }
        }

        @Test
        fun `should return empty list when no modules exist`() {
            every { engineClient.getLibraryModules() } returns emptyList()

            val result = useCase.getAllModules()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class GetModuleCode {
        @Test
        fun `should return module source code`() {
            val code = """
                \"\"\"Timer utilities.\"\"\"
                
                def debounce_check(ctx, key, delay_ms):
                    return True
            """.trimIndent()
            every { engineClient.getLibraryCode("timers") } returns code

            val result = useCase.getModuleCode("timers")

            assertEquals(code, result)
            verify { engineClient.getLibraryCode("timers") }
        }

        @Test
        fun `should throw when module not found`() {
            every { engineClient.getLibraryCode("nonexistent") } returns ""

            assertThrows<LibraryModuleNotFoundException> {
                useCase.getModuleCode("nonexistent")
            }
        }

        @Test
        fun `should handle module name with special characters`() {
            val code = "# module code"
            every { engineClient.getLibraryCode("my_module_v2") } returns code

            val result = useCase.getModuleCode("my_module_v2")

            assertEquals(code, result)
        }
    }
}
