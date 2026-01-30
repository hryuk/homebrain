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

    @LlmTool(description = "Get all available library modules with their functions. Library modules provide reusable functions that can be called from automations via ctx.lib.modulename.function(). Use this to check what shared functionality already exists before creating new automations.")
    fun getLibraryModules(): List<LibraryModuleInfo> {
        return engineClient.getLibraryModules()
            .map { module ->
                LibraryModuleInfo(
                    name = module.name,
                    description = module.description,
                    functions = module.functions
                )
            }
    }

    @LlmTool(description = "Get the source code for a specific library module. Use this to see how a library function works or to understand what parameters it takes. Returns the complete Starlark source code with docstrings.")
    fun getLibraryCode(
        @LlmTool.Param(description = "The name of the library module (e.g., 'timers', 'utils', 'presence')")
        moduleName: String
    ): String {
        val code = engineClient.getLibraryCode(moduleName)
        return if (code.isBlank()) {
            "Library module '$moduleName' not found"
        } else {
            code
        }
    }

    @LlmTool(description = "Get the global state schema showing which automations have write permissions to which global state keys. Use this to understand existing global state usage and avoid conflicts when creating new automations.")
    fun getGlobalStateSchema(): Map<String, List<String>> {
        return engineClient.getGlobalStateSchema().keyPatterns
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

/**
 * Information about a library module returned by the LLM tool.
 */
data class LibraryModuleInfo(
    val name: String,
    val description: String,
    val functions: List<String>
)
