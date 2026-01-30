package com.homebrain.agent.api.mapper

import com.homebrain.agent.domain.library.LibraryModule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LibraryMapperTest {

    private lateinit var mapper: LibraryMapper

    @BeforeEach
    fun setUp() {
        mapper = LibraryMapper()
    }

    @Nested
    inner class ToModuleDto {
        @Test
        fun `should map library module to DTO`() {
            val module = LibraryModule.create(
                name = "timers",
                description = "Timer utilities for debouncing and throttling",
                functions = listOf("debounce_check", "throttle_check", "delay_action")
            )

            val result = mapper.toModuleDto(module)

            assertEquals("timers", result.name)
            assertEquals("Timer utilities for debouncing and throttling", result.description)
            assertEquals(3, result.functions.size)
            assertTrue(result.functions.contains("debounce_check"))
            assertTrue(result.functions.contains("throttle_check"))
            assertTrue(result.functions.contains("delay_action"))
        }

        @Test
        fun `should handle empty description`() {
            val module = LibraryModule.create(
                name = "utils",
                description = "",
                functions = listOf("safe_get")
            )

            val result = mapper.toModuleDto(module)

            assertEquals("utils", result.name)
            assertEquals("", result.description)
        }

        @Test
        fun `should handle empty functions list`() {
            val module = LibraryModule.create(
                name = "empty_module",
                description = "Module with no functions",
                functions = emptyList()
            )

            val result = mapper.toModuleDto(module)

            assertTrue(result.functions.isEmpty())
        }
    }

    @Nested
    inner class ToModuleDtoList {
        @Test
        fun `should map list of modules`() {
            val modules = listOf(
                LibraryModule.create("timers", "Timer utils", listOf("debounce")),
                LibraryModule.create("utils", "Helper utils", listOf("safe_get", "clamp"))
            )

            val result = mapper.toModuleDtoList(modules)

            assertEquals(2, result.size)
            assertEquals("timers", result[0].name)
            assertEquals("utils", result[1].name)
        }

        @Test
        fun `should return empty list for empty input`() {
            val result = mapper.toModuleDtoList(emptyList())
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class ToCodeDto {
        @Test
        fun `should map module name and code to DTO`() {
            val moduleName = "timers"
            val code = """
                \"\"\"Timer utilities.\"\"\"
                
                def debounce_check(ctx, key, delay_ms):
                    \"\"\"Check if enough time has passed.\"\"\"
                    return True
            """.trimIndent()

            val result = mapper.toCodeDto(moduleName, code)

            assertEquals("timers", result.name)
            assertEquals(code, result.code)
        }

        @Test
        fun `should handle empty code`() {
            val result = mapper.toCodeDto("empty", "")

            assertEquals("empty", result.name)
            assertEquals("", result.code)
        }

        @Test
        fun `should preserve multiline code formatting`() {
            val code = "line1\nline2\n    indented\nline4"

            val result = mapper.toCodeDto("test", code)

            assertTrue(result.code.contains("\n"))
            assertTrue(result.code.contains("    indented"))
        }
    }
}
