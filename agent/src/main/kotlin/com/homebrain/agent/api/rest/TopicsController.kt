package com.homebrain.agent.api.rest

import com.homebrain.agent.api.mapper.TopicMapper
import com.homebrain.agent.application.TopicUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class TopicsController(
    private val topicUseCase: TopicUseCase,
    private val topicMapper: TopicMapper
) {

    @GetMapping("/topics")
    fun listTopics(): ResponseEntity<List<String>> {
        val topics = topicUseCase.getAllTopics()
        // Return as simple string list for backward compatibility
        return ResponseEntity.ok(topicMapper.toPathList(topics))
    }
}
