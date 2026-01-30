package com.homebrain.agent.api.rest

import com.homebrain.agent.api.dto.CommitInfoDto
import com.homebrain.agent.api.mapper.AutomationMapper
import com.homebrain.agent.application.AutomationUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HistoryController(
    private val automationUseCase: AutomationUseCase,
    private val automationMapper: AutomationMapper
) {

    @GetMapping("/history")
    fun getHistory(): ResponseEntity<List<CommitInfoDto>> {
        val history = automationUseCase.getHistory(50)
        return ResponseEntity.ok(automationMapper.toCommitDtoList(history))
    }
}
