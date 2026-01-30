package com.homebrain.agent.infrastructure.ai

import com.embabel.agent.api.annotation.LlmTool
import com.homebrain.agent.domain.topic.TopicRepository
import com.homebrain.agent.infrastructure.engine.EngineClient
import org.springframework.stereotype.Component

/**
 * LLM tools for querying the smart home system.
 * 
 * These functions are callable by the LLM during chat conversations
 * to gather information about available devices and automations.
 */
@Component
class MqttLlmTools(
    private val topicRepository: TopicRepository,
    private val engineClient: EngineClient
) {

    @LlmTool(description = "Get all available MQTT topics in the smart home system. Returns a list of topic strings like 'zigbee2mqtt/living_room/light', 'zigbee2mqtt/bedroom/motion_sensor', etc.")
    fun getAllTopics(): List<String> {
        return topicRepository.findAll().map { it.path.value }
    }

    @LlmTool(description = "Search for MQTT topics matching a pattern (case-insensitive). Use this to find specific devices like lights, sensors, or switches. Examples: 'light', 'motion', 'temperature', 'bedroom'.")
    fun searchTopics(
        @LlmTool.Param(description = "The pattern to search for in topic names") 
        pattern: String
    ): List<String> {
        return topicRepository.search(pattern).map { it.path.value }
    }

    @LlmTool(description = "Get all existing automation names and their enabled status. Returns a list of automation info to help understand what automations are already running.")
    fun getAutomations(): List<AutomationInfo> {
        return engineClient.getAutomations()
            .map { automation ->
                AutomationInfo(
                    name = automation["name"]?.toString() ?: "Unknown",
                    description = automation["description"]?.toString(),
                    enabled = automation["enabled"] as? Boolean ?: false
                )
            }
    }
}

/**
 * Information about an automation returned by the LLM tool.
 */
data class AutomationInfo(
    val name: String,
    val description: String?,
    val enabled: Boolean
)
