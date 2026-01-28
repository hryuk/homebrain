package com.homebrain.agent.controller

import com.homebrain.agent.service.EngineProxyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class TopicsController(
    private val engineProxyService: EngineProxyService
) {

    @GetMapping("/topics")
    fun listTopics(): ResponseEntity<List<String>> {
        val topics = engineProxyService.getTopics()
        return ResponseEntity.ok(topics)
    }
}
