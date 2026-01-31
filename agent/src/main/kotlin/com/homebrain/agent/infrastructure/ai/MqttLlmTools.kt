package com.homebrain.agent.infrastructure.ai

import com.embabel.agent.api.annotation.LlmTool
import com.homebrain.agent.application.CodeEmbeddingService
import com.homebrain.agent.domain.embedding.CodeType
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
    private val engineClient: EngineClient,
    private val embeddingService: CodeEmbeddingService
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

    @LlmTool(description = """
        Search for existing automations and library modules that are semantically similar to a query.
        Use this BEFORE creating new automations to check if similar functionality already exists.
        Returns full source code of similar automations/libraries with similarity scores.
        
        IMPORTANT: Always call this first when the user requests new functionality.
        If similarity > 0.7, strongly consider modifying existing code instead of creating new.
        
        Example queries:
        - "turn on lights when motion detected"
        - "debounce timer helper"
        - "sync device state"
        - "bedtime scene"
    """)
    fun searchSimilarCode(
        @LlmTool.Param(description = "Natural language description of the functionality to search for")
        query: String,
        @LlmTool.Param(description = "Maximum number of results to return (default 5)")
        topK: Int = 5
    ): List<CodeSearchResultInfo> {
        if (!embeddingService.isReady()) {
            return emptyList()
        }
        
        return embeddingService.search(query, topK).map { result ->
            CodeSearchResultInfo(
                type = when (result.type) {
                    CodeType.AUTOMATION -> "automation"
                    CodeType.LIBRARY -> "library"
                },
                name = result.name,
                sourceCode = result.sourceCode,
                similarity = result.similarity
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

/**
 * Information about a library module returned by the LLM tool.
 */
data class LibraryModuleInfo(
    val name: String,
    val description: String,
    val functions: List<String>
)

/**
 * Information about a code search result returned by the LLM tool.
 */
data class CodeSearchResultInfo(
    val type: String,          // "automation" or "library"
    val name: String,          // e.g., "motion_light" or "timers"
    val sourceCode: String,    // Full source code
    val similarity: Float      // 0.0 to 1.0 (higher = more similar)
)
