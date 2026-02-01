package com.homebrain.agent.application

import com.homebrain.agent.domain.commit.Commit
import com.homebrain.agent.domain.library.LibraryModule
import com.homebrain.agent.infrastructure.engine.EngineClient
import com.homebrain.agent.infrastructure.persistence.GitOperations
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class LibraryUseCaseTest {

    private lateinit var engineClient: EngineClient
    private lateinit var gitOperations: GitOperations
    private lateinit var codeEmbeddingService: CodeEmbeddingService
    private lateinit var useCase: LibraryUseCase

    private val sampleCommit = Commit(
        hash = "abc123def",
        message = "Delete library: lib/test.lib.star",
        author = "homebrain",
        timestamp = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        engineClient = mockk()
        gitOperations = mockk()
        codeEmbeddingService = mockk(relaxed = true)
        useCase = LibraryUseCase(engineClient, gitOperations, codeEmbeddingService)
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

    @Nested
    inner class Delete {
        @Test
        fun `should delete existing library module`() {
            every { gitOperations.fileExists("lib/timers.lib.star") } returns true
            every { gitOperations.deleteFile("lib/timers.lib.star") } just runs
            every { gitOperations.commit(any()) } returns sampleCommit

            val result = useCase.delete("timers")

            assertEquals(sampleCommit, result)
            verify { gitOperations.deleteFile("lib/timers.lib.star") }
            verify { gitOperations.commit("Delete library: lib/timers.lib.star") }
        }

        @Test
        fun `should throw when library not found`() {
            every { gitOperations.fileExists("lib/nonexistent.lib.star") } returns false

            assertThrows<LibraryModuleNotFoundException> {
                useCase.delete("nonexistent")
            }
        }

        @Test
        fun `should trigger embedding removal after delete`() {
            every { gitOperations.fileExists("lib/utils.lib.star") } returns true
            every { gitOperations.deleteFile("lib/utils.lib.star") } just runs
            every { gitOperations.commit(any()) } returns sampleCommit

            useCase.delete("utils")

            verify { codeEmbeddingService.removeLibrary("utils") }
        }

        @Test
        fun `should return commit with correct message`() {
            every { gitOperations.fileExists("lib/helpers.lib.star") } returns true
            every { gitOperations.deleteFile("lib/helpers.lib.star") } just runs
            every { gitOperations.commit("Delete library: lib/helpers.lib.star") } returns sampleCommit

            val result = useCase.delete("helpers")

            assertEquals(sampleCommit.hash, result.hash)
        }
    }
}
