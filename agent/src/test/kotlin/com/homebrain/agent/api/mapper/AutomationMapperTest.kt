package com.homebrain.agent.api.mapper

import com.homebrain.agent.domain.automation.Automation
import com.homebrain.agent.domain.automation.AutomationCode
import com.homebrain.agent.domain.automation.AutomationId
import com.homebrain.agent.domain.commit.Commit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AutomationMapperTest {

    private lateinit var mapper: AutomationMapper

    private val sampleCode = AutomationCode("def on_message(t, p, ctx): pass")
    private val sampleCommit = Commit(
        hash = "abc123def456",
        message = "Add automation",
        author = "homebrain",
        timestamp = Instant.parse("2024-01-15T10:30:00Z")
    )

    @BeforeEach
    fun setUp() {
        mapper = AutomationMapper()
    }

    @Nested
    inner class ToCodeResponse {
        @Test
        fun `should map automation to code response`() {
            val automation = Automation(
                id = AutomationId("light_controller"),
                code = sampleCode
            )

            val result = mapper.toCodeResponse(automation)

            assertEquals("light_controller", result.id)
            assertEquals("def on_message(t, p, ctx): pass", result.code)
        }

        @Test
        fun `should handle multiline code`() {
            val multilineCode = AutomationCode("""
                def on_message(topic, payload, ctx):
                    ctx.log("Received")
                
                config = {"name": "Test"}
            """.trimIndent())
            val automation = Automation(AutomationId("test"), multilineCode)

            val result = mapper.toCodeResponse(automation)

            assertTrue(result.code.contains("def on_message"))
            assertTrue(result.code.contains("config = {"))
        }
    }

    @Nested
    inner class ToResponse {
        @Test
        fun `should map automation to response with commit`() {
            val automation = Automation(AutomationId("test"), sampleCode, sampleCommit)

            val result = mapper.toResponse(automation, "created", sampleCommit)

            assertEquals("created", result.status)
            assertEquals("test.star", result.filename)
            assertEquals("abc123def456", result.commit)
        }

        @Test
        fun `should map automation to response without commit`() {
            val automation = Automation(AutomationId("test"), sampleCode)

            val result = mapper.toResponse(automation, "updated", null)

            assertEquals("updated", result.status)
            assertEquals("test.star", result.filename)
            assertNull(result.commit)
        }

        @Test
        fun `should handle different status values`() {
            val automation = Automation(AutomationId("test"), sampleCode)

            listOf("created", "updated", "deployed", "error").forEach { status ->
                val result = mapper.toResponse(automation, status, null)
                assertEquals(status, result.status)
            }
        }
    }

    @Nested
    inner class ToCommitDto {
        @Test
        fun `should map commit to DTO`() {
            val result = mapper.toCommitDto(sampleCommit)

            assertEquals("abc123def456", result.hash)
            assertEquals("Add automation", result.message)
            assertEquals("homebrain", result.author)
            assertEquals(Instant.parse("2024-01-15T10:30:00Z"), result.date)
        }

        @Test
        fun `should handle empty message`() {
            val commit = sampleCommit.copy(message = "")
            val result = mapper.toCommitDto(commit)
            assertEquals("", result.message)
        }
    }

    @Nested
    inner class ToCommitDtoList {
        @Test
        fun `should map list of commits`() {
            val commit2 = Commit(
                hash = "def456ghi789",
                message = "Update automation",
                author = "user",
                timestamp = Instant.parse("2024-01-16T11:00:00Z")
            )
            val commits = listOf(sampleCommit, commit2)

            val result = mapper.toCommitDtoList(commits)

            assertEquals(2, result.size)
            assertEquals("abc123def456", result[0].hash)
            assertEquals("def456ghi789", result[1].hash)
        }

        @Test
        fun `should return empty list for empty input`() {
            val result = mapper.toCommitDtoList(emptyList())
            assertTrue(result.isEmpty())
        }
    }
}
