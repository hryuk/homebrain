package com.homebrain.agent.api.rest

import com.homebrain.agent.application.LogUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class LogsController(
    private val logUseCase: LogUseCase
) {

    @GetMapping("/logs")
    fun getLogs(): ResponseEntity<List<Map<String, Any>>> {
        val logs = logUseCase.getLogs()
        return ResponseEntity.ok(logs)
    }
}
