package com.homebrain.agent.application

import com.homebrain.agent.domain.automation.Automation
import com.homebrain.agent.domain.automation.AutomationCode
import com.homebrain.agent.domain.automation.AutomationId
import com.homebrain.agent.domain.automation.AutomationRepository
import com.homebrain.agent.domain.commit.Commit
import com.homebrain.agent.infrastructure.engine.EngineClient
import com.homebrain.agent.infrastructure.persistence.GitOperations
import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class AutomationUseCaseTest {

    private lateinit var automationRepository: AutomationRepository
    private lateinit var engineClient: EngineClient
    private lateinit var gitOperations: GitOperations
    private lateinit var useCase: AutomationUseCase

    private val sampleCommit = Commit(
        hash = "abc123def",
        message = "Test commit",
        author = "homebrain",
        timestamp = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        automationRepository = mockk()
        engineClient = mockk()
        gitOperations = mockk()
        useCase = AutomationUseCase(automationRepository, engineClient, gitOperations)
    }

    @Nested
    inner class Create {
        @Test
        fun `should create automation with sanitized filename`() {
            val code = "def on_message(t, p, ctx): pass"
            
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create(code, "My Cool Automation!")
            
            assertEquals("my_cool_automation", result.automation.id.value)
            assertTrue(result.isNew)
            assertEquals(sampleCommit, result.commit)
        }

        @Test
        fun `should use default filename when null`() {
            val code = "def on_message(t, p, ctx): pass"
            
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create(code, null)
            
            assertEquals("automation", result.automation.id.value)
        }

        @Test
        fun `should strip star extension from filename`() {
            val code = "def on_message(t, p, ctx): pass"
            
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create(code, "test.star")
            
            assertEquals("test", result.automation.id.value)
        }
    }

    @Nested
    inner class Update {
        @Test
        fun `should update existing automation`() {
            val code = "def on_schedule(ctx): pass"
            
            every { automationRepository.exists(AutomationId("test")) } returns true
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.update("test", code)
            
            assertEquals("test", result.automation.id.value)
            assertEquals(code, result.automation.code.value)
            assertFalse(result.isNew)
        }

        @Test
        fun `should throw when automation not found`() {
            every { automationRepository.exists(AutomationId("nonexistent")) } returns false
            
            assertThrows<AutomationNotFoundException> {
                useCase.update("nonexistent", "code")
            }
        }
    }

    @Nested
    inner class GetById {
        @Test
        fun `should return automation when found`() {
            val automation = Automation(
                id = AutomationId("test"),
                code = AutomationCode("def on_message(t,p,c): pass")
            )
            every { automationRepository.findById(AutomationId("test")) } returns automation
            
            val result = useCase.getById("test")
            
            assertEquals(automation, result)
        }

        @Test
        fun `should throw when not found`() {
            every { automationRepository.findById(AutomationId("missing")) } returns null
            
            assertThrows<AutomationNotFoundException> {
                useCase.getById("missing")
            }
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `should delete existing automation`() {
            every { automationRepository.exists(AutomationId("test")) } returns true
            every { automationRepository.delete(AutomationId("test")) } returns sampleCommit
            
            val result = useCase.delete("test")
            
            assertEquals(sampleCommit, result)
            verify { automationRepository.delete(AutomationId("test")) }
        }

        @Test
        fun `should throw when automation not found`() {
            every { automationRepository.exists(AutomationId("missing")) } returns false
            
            assertThrows<AutomationNotFoundException> {
                useCase.delete("missing")
            }
        }
    }

    @Nested
    inner class ListAll {
        @Test
        fun `should delegate to engine client`() {
            val automations = listOf(
                mapOf("id" to "auto1", "status" to "running"),
                mapOf("id" to "auto2", "status" to "stopped")
            )
            every { engineClient.getAutomations() } returns automations
            
            val result = useCase.listAll()
            
            assertEquals(automations, result)
        }
    }

    @Nested
    inner class GetHistory {
        @Test
        fun `should return commits from git operations`() {
            val commits = listOf(sampleCommit)
            every { gitOperations.getHistory(50) } returns commits
            
            val result = useCase.getHistory()
            
            assertEquals(commits, result)
        }

        @Test
        fun `should use custom limit`() {
            every { gitOperations.getHistory(10) } returns emptyList()
            
            useCase.getHistory(10)
            
            verify { gitOperations.getHistory(10) }
        }
    }

    @Nested
    inner class SanitizeFilenameUnitTests {
        // We test sanitizeFilename indirectly through create()
        
        @Test
        fun `should lowercase the filename`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", "MyAutomation")
            assertEquals("myautomation", result.automation.id.value)
        }

        @Test
        fun `should replace spaces with underscores`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", "my automation")
            assertEquals("my_automation", result.automation.id.value)
        }

        @Test
        fun `should replace special characters with underscores`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", "my@automation#123!")
            assertEquals("my_automation_123", result.automation.id.value)
        }

        @Test
        fun `should collapse multiple underscores`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", "my___automation")
            assertEquals("my_automation", result.automation.id.value)
        }

        @Test
        fun `should trim leading and trailing underscores`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", "___automation___")
            assertEquals("automation", result.automation.id.value)
        }

        @Test
        fun `should return automation for empty result`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", "!!!")
            assertEquals("automation", result.automation.id.value)
        }

        @Test
        fun `should preserve numbers`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", "sensor123")
            assertEquals("sensor123", result.automation.id.value)
        }

        @Test
        fun `should handle unicode characters`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", "café_sensor")
            assertEquals("caf_sensor", result.automation.id.value)
        }
    }

    @Nested
    inner class SanitizeFilenamePropertyTests {
        
        @Test
        fun `sanitized filename should never contain invalid characters`() = runBlocking {
            every { automationRepository.save(any()) } returns sampleCommit
            
            forAll(Arb.string(0..100)) { input ->
                val result = useCase.create("code", input)
                val sanitized = result.automation.id.value
                
                // Should only contain lowercase letters, numbers, and underscores
                sanitized.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
            }
        }

        @Test
        fun `sanitized filename should always be lowercase`() = runBlocking {
            every { automationRepository.save(any()) } returns sampleCommit
            
            forAll(Arb.string(1..100)) { input ->
                val result = useCase.create("code", input)
                val sanitized = result.automation.id.value
                sanitized == sanitized.lowercase()
            }
        }

        @Test
        fun `sanitized filename should never have consecutive underscores`() = runBlocking {
            every { automationRepository.save(any()) } returns sampleCommit
            
            forAll(Arb.string(0..100)) { input ->
                val result = useCase.create("code", input)
                val sanitized = result.automation.id.value
                !sanitized.contains("__")
            }
        }

        @Test
        fun `sanitized filename should never start or end with underscore`() = runBlocking {
            every { automationRepository.save(any()) } returns sampleCommit
            
            forAll(Arb.string(0..100)) { input ->
                val result = useCase.create("code", input)
                val sanitized = result.automation.id.value
                !sanitized.startsWith("_") && !sanitized.endsWith("_")
            }
        }

        @Test
        fun `result should never be empty - defaults to automation`() = runBlocking {
            every { automationRepository.save(any()) } returns sampleCommit
            
            forAll(Arb.string(0..100)) { input ->
                val result = useCase.create("code", input)
                result.automation.id.value.isNotEmpty()
            }
        }

        @Test
        fun `alphanumeric input should be preserved except for case`() = runBlocking {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val alphanumericArb = Arb.string(1..50)
                .map { s: String -> s.filter { c -> c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' } }
                .map { s: String -> if (s.isEmpty()) "test" else s }
            
            forAll(alphanumericArb) { input: String ->
                val result = useCase.create("code", input)
                result.automation.id.value == input.lowercase()
            }
        }
    }

    @Nested
    inner class DeployMultiple {
        @Test
        fun `should deploy single automation file`() {
            val files = listOf(
                FileDeployment("def on_message(t,p,c): pass", "test_automation.star", FileDeployment.FileType.AUTOMATION)
            )
            
            every { gitOperations.fileExists(any()) } returns false
            every { gitOperations.writeFile(any(), any()) } just runs
            every { gitOperations.stageFile(any()) } just runs
            every { gitOperations.commit(any()) } returns sampleCommit
            
            val result = useCase.deployMultiple(files)
            
            assertEquals(1, result.deployedFiles.size)
            assertEquals("test_automation.star", result.deployedFiles[0].filename)
            assertEquals(sampleCommit, result.commit)
            
            verify { gitOperations.writeFile("test_automation.star", "def on_message(t,p,c): pass") }
            verify { gitOperations.stageFile("test_automation.star") }
            verify { gitOperations.commit(match { it.contains("test_automation") }) }
        }

        @Test
        fun `should deploy library and automation together`() {
            val files = listOf(
                FileDeployment("def blink(ctx): pass", "lib/lights.lib.star", FileDeployment.FileType.LIBRARY),
                FileDeployment("def on_message(t,p,c): ctx.lib.lights.blink(c)", "blink_light.star", FileDeployment.FileType.AUTOMATION)
            )
            
            every { gitOperations.fileExists(any()) } returns false
            every { gitOperations.writeFile(any(), any()) } just runs
            every { gitOperations.stageFile(any()) } just runs
            every { gitOperations.commit(any()) } returns sampleCommit
            
            val result = useCase.deployMultiple(files)
            
            assertEquals(2, result.deployedFiles.size)
            assertEquals("lib/lights.lib.star", result.deployedFiles[0].filename)
            assertEquals("blink_light.star", result.deployedFiles[1].filename)
            
            // Verify library is written first
            verifyOrder {
                gitOperations.writeFile("lib/lights.lib.star", "def blink(ctx): pass")
                gitOperations.stageFile("lib/lights.lib.star")
                gitOperations.writeFile("blink_light.star", match { it.contains("ctx.lib.lights.blink") })
                gitOperations.stageFile("blink_light.star")
            }
            
            // Verify single commit for all files
            verify(exactly = 1) { gitOperations.commit(any()) }
        }

        @Test
        fun `should include library in commit message`() {
            val files = listOf(
                FileDeployment("def helper(): pass", "lib/utils.lib.star", FileDeployment.FileType.LIBRARY),
                FileDeployment("def on_message(t,p,c): pass", "my_automation.star", FileDeployment.FileType.AUTOMATION)
            )
            
            val commitMessageSlot = slot<String>()
            
            every { gitOperations.fileExists(any()) } returns false
            every { gitOperations.writeFile(any(), any()) } just runs
            every { gitOperations.stageFile(any()) } just runs
            every { gitOperations.commit(capture(commitMessageSlot)) } returns sampleCommit
            
            useCase.deployMultiple(files)
            
            assertTrue(commitMessageSlot.captured.contains("lib/utils.lib.star"))
            assertTrue(commitMessageSlot.captured.contains("my_automation.star"))
        }

        @Test
        fun `should handle empty files list`() {
            assertThrows<IllegalArgumentException> {
                useCase.deployMultiple(emptyList())
            }
        }

        @Test
        fun `should update existing files`() {
            val files = listOf(
                FileDeployment("def updated(): pass", "existing.star", FileDeployment.FileType.AUTOMATION)
            )
            
            every { gitOperations.fileExists("existing.star") } returns true
            every { gitOperations.writeFile(any(), any()) } just runs
            every { gitOperations.stageFile(any()) } just runs
            every { gitOperations.commit(any()) } returns sampleCommit
            
            val result = useCase.deployMultiple(files)
            
            assertFalse(result.deployedFiles[0].isNew)
        }
    }
}
