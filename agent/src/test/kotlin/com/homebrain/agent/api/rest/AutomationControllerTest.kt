package com.homebrain.agent.api.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.homebrain.agent.api.dto.AutomationRequest
import com.homebrain.agent.api.mapper.AutomationMapper
import com.homebrain.agent.application.AutomationNotFoundException
import com.homebrain.agent.application.AutomationResult
import com.homebrain.agent.application.AutomationUseCase
import com.homebrain.agent.domain.automation.Automation
import com.homebrain.agent.domain.automation.AutomationCode
import com.homebrain.agent.domain.automation.AutomationId
import com.homebrain.agent.domain.commit.Commit
import com.homebrain.agent.exception.HomebrainExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class AutomationControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var automationUseCase: AutomationUseCase
    private val automationMapper = AutomationMapper()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val sampleCode = "def on_message(t, p, ctx): pass"
    private val sampleCommit = Commit(
        hash = "abc123def",
        message = "Test commit",
        author = "homebrain",
        timestamp = Instant.parse("2024-01-15T10:30:00Z")
    )

    @BeforeEach
    fun setUp() {
        automationUseCase = mockk()
        val controller = AutomationController(automationUseCase, automationMapper)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HomebrainExceptionHandler())
            .build()
    }

    @Nested
    inner class ListAutomations {
        @Test
        fun `should return list of automations`() {
            val automations = listOf(
                mapOf("id" to "auto1", "name" to "Light Controller", "enabled" to true),
                mapOf("id" to "auto2", "name" to "Temperature Monitor", "enabled" to false)
            )
            every { automationUseCase.listAll() } returns automations

            mockMvc.perform(get("/api/automations"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value("auto1"))
                .andExpect(jsonPath("$[0].name").value("Light Controller"))
                .andExpect(jsonPath("$[1].id").value("auto2"))
        }

        @Test
        fun `should return empty list when no automations`() {
            every { automationUseCase.listAll() } returns emptyList()

            mockMvc.perform(get("/api/automations"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$").isEmpty)
        }
    }

    @Nested
    inner class CreateAutomation {
        @Test
        fun `should create automation and return response`() {
            val request = AutomationRequest(filename = "light_controller", code = sampleCode)
            val automation = Automation(
                id = AutomationId("light_controller"),
                code = AutomationCode(sampleCode),
                commit = sampleCommit
            )
            val result = AutomationResult(automation, sampleCommit, isNew = true)

            every { automationUseCase.create(sampleCode, "light_controller") } returns result

            mockMvc.perform(
                post("/api/automations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("deployed"))
                .andExpect(jsonPath("$.filename").value("light_controller.star"))
                .andExpect(jsonPath("$.commit").value("abc123def"))
        }

        @Test
        fun `should create automation with null filename`() {
            val request = AutomationRequest(code = sampleCode)
            val automation = Automation(
                id = AutomationId("automation"),
                code = AutomationCode(sampleCode),
                commit = sampleCommit
            )
            val result = AutomationResult(automation, sampleCommit, isNew = true)

            every { automationUseCase.create(sampleCode, null) } returns result

            mockMvc.perform(
                post("/api/automations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("deployed"))
        }
    }

    @Nested
    inner class GetAutomation {
        @Test
        fun `should return automation code`() {
            val automation = Automation(
                id = AutomationId("test"),
                code = AutomationCode(sampleCode)
            )
            every { automationUseCase.getById("test") } returns automation

            mockMvc.perform(get("/api/automations/test"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value("test"))
                .andExpect(jsonPath("$.code").value(sampleCode))
        }

        @Test
        fun `should return 404 when automation not found`() {
            every { automationUseCase.getById("nonexistent") } throws AutomationNotFoundException("nonexistent")

            mockMvc.perform(get("/api/automations/nonexistent"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class UpdateAutomation {
        @Test
        fun `should update automation and return response`() {
            val newCode = "def on_schedule(ctx): pass"
            val request = AutomationRequest(code = newCode)
            val automation = Automation(
                id = AutomationId("test"),
                code = AutomationCode(newCode),
                commit = sampleCommit
            )
            val result = AutomationResult(automation, sampleCommit, isNew = false)

            every { automationUseCase.update("test", newCode) } returns result

            mockMvc.perform(
                put("/api/automations/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("updated"))
                .andExpect(jsonPath("$.filename").value("test.star"))
                .andExpect(jsonPath("$.commit").value("abc123def"))
        }

        @Test
        fun `should return 404 when updating nonexistent automation`() {
            val request = AutomationRequest(code = "code")
            every { automationUseCase.update("missing", "code") } throws AutomationNotFoundException("missing")

            mockMvc.perform(
                put("/api/automations/missing")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class DeleteAutomation {
        @Test
        fun `should delete automation and return response`() {
            every { automationUseCase.delete("test") } returns sampleCommit

            mockMvc.perform(delete("/api/automations/test"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("deleted"))
                .andExpect(jsonPath("$.filename").value("test.star"))
                .andExpect(jsonPath("$.commit").value("abc123def"))

            verify { automationUseCase.delete("test") }
        }

        @Test
        fun `should return 404 when deleting nonexistent automation`() {
            every { automationUseCase.delete("missing") } throws AutomationNotFoundException("missing")

            mockMvc.perform(delete("/api/automations/missing"))
                .andExpect(status().isNotFound)
        }
    }
}
