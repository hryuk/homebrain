package com.homebrain.agent.controller

import com.homebrain.agent.dto.AutomationCodeResponse
import com.homebrain.agent.dto.AutomationRequest
import com.homebrain.agent.dto.AutomationResponse
import com.homebrain.agent.service.EngineProxyService
import com.homebrain.agent.service.GitService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/automations")
class AutomationController(
    private val gitService: GitService,
    private val engineProxyService: EngineProxyService
) {

    @GetMapping
    fun listAutomations(): ResponseEntity<List<Map<String, Any>>> {
        val automations = engineProxyService.getAutomations()
        return ResponseEntity.ok(automations)
    }

    @PostMapping
    fun createAutomation(@RequestBody request: AutomationRequest): ResponseEntity<AutomationResponse> {
        val filename = ensureStarExtension(request.filename ?: "automation")
        logger.info { "Creating automation: $filename" }

        val commitHash = gitService.writeAndCommit(
            filename = filename,
            content = request.code,
            message = "Add automation: $filename"
        )

        return ResponseEntity.ok(AutomationResponse(
            status = "deployed",
            filename = filename,
            commit = commitHash
        ))
    }

    @GetMapping("/{id}")
    fun getAutomation(@PathVariable id: String): ResponseEntity<AutomationCodeResponse> {
        val filename = "$id.star"
        logger.debug { "Reading automation: $filename" }

        val code = gitService.readFile(filename)
        return ResponseEntity.ok(AutomationCodeResponse(
            id = id,
            code = code
        ))
    }

    @PutMapping("/{id}")
    fun updateAutomation(
        @PathVariable id: String,
        @RequestBody request: AutomationRequest
    ): ResponseEntity<AutomationResponse> {
        val filename = "$id.star"
        logger.info { "Updating automation: $filename" }

        val commitHash = gitService.writeAndCommit(
            filename = filename,
            content = request.code,
            message = "Update automation: $filename"
        )

        return ResponseEntity.ok(AutomationResponse(
            status = "updated",
            filename = filename,
            commit = commitHash
        ))
    }

    @DeleteMapping("/{id}")
    fun deleteAutomation(@PathVariable id: String): ResponseEntity<AutomationResponse> {
        val filename = "$id.star"
        logger.info { "Deleting automation: $filename" }

        gitService.deleteFile(filename)
        gitService.commit("Delete automation: $filename")

        return ResponseEntity.ok(AutomationResponse(
            status = "deleted",
            filename = filename
        ))
    }

    private fun ensureStarExtension(filename: String): String {
        return if (filename.endsWith(".star")) filename else "$filename.star"
    }
}
