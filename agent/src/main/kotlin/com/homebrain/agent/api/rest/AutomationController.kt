package com.homebrain.agent.api.rest

import com.homebrain.agent.api.dto.AutomationCodeResponse
import com.homebrain.agent.api.dto.AutomationRequest
import com.homebrain.agent.api.dto.AutomationResponse
import com.homebrain.agent.api.mapper.AutomationMapper
import com.homebrain.agent.application.AutomationUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/automations")
class AutomationController(
    private val automationUseCase: AutomationUseCase,
    private val automationMapper: AutomationMapper
) {

    @GetMapping
    fun listAutomations(): ResponseEntity<List<Map<String, Any>>> {
        val automations = automationUseCase.listAll()
        return ResponseEntity.ok(automations)
    }

    @PostMapping
    fun createAutomation(@RequestBody request: AutomationRequest): ResponseEntity<AutomationResponse> {
        logger.info { "Creating automation: ${request.filename}" }

        val result = automationUseCase.create(
            code = request.code,
            filename = request.filename
        )

        return ResponseEntity.ok(
            automationMapper.toResponse(result.automation, "deployed", result.commit)
        )
    }

    @GetMapping("/{id}")
    fun getAutomation(@PathVariable id: String): ResponseEntity<AutomationCodeResponse> {
        logger.debug { "Reading automation: $id" }

        val automation = automationUseCase.getById(id)
        return ResponseEntity.ok(automationMapper.toCodeResponse(automation))
    }

    @PutMapping("/{id}")
    fun updateAutomation(
        @PathVariable id: String,
        @RequestBody request: AutomationRequest
    ): ResponseEntity<AutomationResponse> {
        logger.info { "Updating automation: $id" }

        val result = automationUseCase.update(id, request.code)

        return ResponseEntity.ok(
            automationMapper.toResponse(result.automation, "updated", result.commit)
        )
    }

    @DeleteMapping("/{id}")
    fun deleteAutomation(@PathVariable id: String): ResponseEntity<AutomationResponse> {
        logger.info { "Deleting automation: $id" }

        val commit = automationUseCase.delete(id)

        return ResponseEntity.ok(
            AutomationResponse(
                status = "deleted",
                filename = "$id.star",
                commit = commit.hash
            )
        )
    }
}
