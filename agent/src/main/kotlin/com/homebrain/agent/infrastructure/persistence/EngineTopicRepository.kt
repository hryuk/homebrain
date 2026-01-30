package com.homebrain.agent.infrastructure.persistence

import com.homebrain.agent.domain.topic.Topic
import com.homebrain.agent.domain.topic.TopicPath
import com.homebrain.agent.domain.topic.TopicRepository
import com.homebrain.agent.infrastructure.engine.EngineClient
import org.springframework.stereotype.Repository

/**
 * Topic repository implementation that fetches topics from the engine.
 * 
 * The engine discovers topics at runtime as MQTT messages are received.
 */
@Repository
class EngineTopicRepository(
    private val engineClient: EngineClient
) : TopicRepository {

    override fun findAll(): List<Topic> {
        return engineClient.getTopics()
            .map { Topic.fromPath(it) }
    }

    override fun search(keyword: String): List<Topic> {
        return findAll().filter { it.containsKeyword(keyword) }
    }

    override fun findByPattern(pattern: TopicPath): List<Topic> {
        return findAll().filter { it.path.matches(pattern) }
    }
}
