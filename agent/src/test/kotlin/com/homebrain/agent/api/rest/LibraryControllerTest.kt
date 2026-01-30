package com.homebrain.agent.api.rest

import com.homebrain.agent.api.mapper.LibraryMapper
import com.homebrain.agent.application.LibraryModuleNotFoundException
import com.homebrain.agent.application.LibraryUseCase
import com.homebrain.agent.domain.library.LibraryModule
import com.homebrain.agent.exception.HomebrainExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LibraryControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var libraryUseCase: LibraryUseCase
    private val libraryMapper = LibraryMapper()

    @BeforeEach
    fun setUp() {
        libraryUseCase = mockk()
        val controller = LibraryController(libraryUseCase, libraryMapper)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HomebrainExceptionHandler())
            .build()
    }

    @Nested
    inner class ListModules {
        @Test
        fun `should return list of library modules`() {
            val modules = listOf(
                LibraryModule.create("timers", "Timer utilities", listOf("debounce_check", "throttle_check")),
                LibraryModule.create("utils", "Helper utilities", listOf("safe_get"))
            )
            every { libraryUseCase.getAllModules() } returns modules

            mockMvc.perform(get("/api/libraries"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].name").value("timers"))
                .andExpect(jsonPath("$[0].description").value("Timer utilities"))
                .andExpect(jsonPath("$[0].functions[0]").value("debounce_check"))
                .andExpect(jsonPath("$[0].functions[1]").value("throttle_check"))
                .andExpect(jsonPath("$[1].name").value("utils"))
                .andExpect(jsonPath("$[1].functions[0]").value("safe_get"))

            verify { libraryUseCase.getAllModules() }
        }

        @Test
        fun `should return empty list when no modules`() {
            every { libraryUseCase.getAllModules() } returns emptyList()

            mockMvc.perform(get("/api/libraries"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$").isEmpty)
        }

        @Test
        fun `should handle modules with empty functions`() {
            val modules = listOf(
                LibraryModule.create("empty", "Empty module", emptyList())
            )
            every { libraryUseCase.getAllModules() } returns modules

            mockMvc.perform(get("/api/libraries"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].name").value("empty"))
                .andExpect(jsonPath("$[0].functions").isArray)
                .andExpect(jsonPath("$[0].functions").isEmpty)
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
            every { libraryUseCase.getModuleCode("timers") } returns code

            mockMvc.perform(get("/api/libraries/timers"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("timers"))
                .andExpect(jsonPath("$.code").value(code))

            verify { libraryUseCase.getModuleCode("timers") }
        }

        @Test
        fun `should return 404 when module not found`() {
            every { libraryUseCase.getModuleCode("nonexistent") } throws 
                LibraryModuleNotFoundException("nonexistent")

            mockMvc.perform(get("/api/libraries/nonexistent"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("not_found"))
        }

        @Test
        fun `should handle module name with underscores`() {
            val code = "# module code"
            every { libraryUseCase.getModuleCode("my_utils_v2") } returns code

            mockMvc.perform(get("/api/libraries/my_utils_v2"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("my_utils_v2"))
                .andExpect(jsonPath("$.code").value(code))
        }
    }
}
