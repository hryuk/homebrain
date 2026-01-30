package com.homebrain.agent.api.mapper

import com.homebrain.agent.api.dto.TopicDto
import com.homebrain.agent.domain.topic.Topic
import org.springframework.stereotype.Component

/**
 * Maps between topic domain models and API DTOs.
 */
@Component
class TopicMapper {

    fun toDto(topic: Topic): TopicDto {
        return TopicDto(
            path = topic.path.value,
            lastValue = topic.lastValue,
            lastSeen = topic.lastSeen
        )
    }

    fun toDtoList(topics: List<Topic>): List<TopicDto> {
        return topics.map { toDto(it) }
    }

    /**
     * Converts topic paths to simple string list for backward compatibility.
     */
    fun toPathList(topics: List<Topic>): List<String> {
        return topics.map { it.path.value }
    }
}
