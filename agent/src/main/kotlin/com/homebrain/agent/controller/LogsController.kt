package com.homebrain.agent.controller

import com.homebrain.agent.service.EngineProxyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class LogsController(
    private val engineProxyService: EngineProxyService
) {

    @GetMapping("/logs")
    fun getLogs(): ResponseEntity<List<Map<String, Any>>> {
        val logs = engineProxyService.getLogs()
        return ResponseEntity.ok(logs)
    }
}
