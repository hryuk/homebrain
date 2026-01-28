package com.homebrain.agent.tools

import com.embabel.agent.api.annotation.LlmTool
import com.homebrain.agent.service.EngineProxyService
import org.springframework.stereotype.Component

@Component
class MqttTools(
    private val engineProxyService: EngineProxyService
) {

    @LlmTool(description = "Get all available MQTT topics in the smart home system. Returns a list of topic strings like 'zigbee2mqtt/living_room/light', 'zigbee2mqtt/bedroom/motion_sensor', etc.")
    fun getAllTopics(): List<String> {
        return engineProxyService.getTopics()
    }

    @LlmTool(description = "Search for MQTT topics matching a pattern (case-insensitive). Use this to find specific devices like lights, sensors, or switches. Examples: 'light', 'motion', 'temperature', 'bedroom'.")
    fun searchTopics(@LlmTool.Param(description = "The pattern to search for in topic names") pattern: String): List<String> {
        return engineProxyService.getTopics()
            .filter { it.contains(pattern, ignoreCase = true) }
    }

    @LlmTool(description = "Get all existing automation names and their enabled status. Returns a list of automation info to help understand what automations are already running.")
    fun getAutomations(): List<AutomationInfo> {
        return engineProxyService.getAutomations()
            .map { automation ->
                AutomationInfo(
                    name = automation["name"]?.toString() ?: "Unknown",
                    description = automation["description"]?.toString(),
                    enabled = automation["enabled"] as? Boolean ?: false
                )
            }
    }
}

data class AutomationInfo(
    val name: String,
    val description: String?,
    val enabled: Boolean
)
