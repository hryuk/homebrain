package com.homebrain.agent.application

import com.homebrain.agent.domain.topic.Topic
import com.homebrain.agent.domain.topic.TopicRepository
import org.springframework.stereotype.Service

/**
 * Use case for topic queries.
 * 
 * Provides operations for discovering and searching MQTT topics.
 */
@Service
class TopicUseCase(
    private val topicRepository: TopicRepository
) {

    /**
     * Gets all discovered topics.
     */
    fun getAllTopics(): List<Topic> {
        return topicRepository.findAll()
    }

    /**
     * Searches topics by keyword.
     */
    fun searchTopics(keyword: String): List<Topic> {
        return topicRepository.search(keyword)
    }
}
