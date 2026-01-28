package com.homebrain.agent.controller

import com.homebrain.agent.dto.CommitInfo
import com.homebrain.agent.service.GitService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HistoryController(
    private val gitService: GitService
) {

    @GetMapping("/history")
    fun getHistory(): ResponseEntity<List<CommitInfo>> {
        val history = gitService.getHistory(50)
        return ResponseEntity.ok(history)
    }
}
