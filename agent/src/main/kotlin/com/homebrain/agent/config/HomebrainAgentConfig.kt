package com.homebrain.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration properties for the Homebrain GOAP agent.
 * 
 * Configure via application.yml:
 * ```yaml
 * homebrain:
 *   agent:
 *     max-fix-attempts: 3
 *     max-concurrency: 4
 *     classification-llm: claude-3-5-haiku-latest
 *     generation-llm: claude-sonnet-4-5-20250514
 * ```
 */
@ConfigurationProperties(prefix = "homebrain.agent")
data class HomebrainAgentConfig(
    /**
     * Maximum number of attempts to fix invalid code before giving up.
     */
    val maxFixAttempts: Int = 3,
    
    /**
     * Maximum concurrency for parallel context gathering operations.
     */
    val maxConcurrency: Int = 4,
    
    /**
     * LLM model to use for intent classification (should be fast/cheap).
     * Default: Claude Haiku 4.5 for fast, cheap classification.
     */
    val classificationLlm: String = "claude-3-5-haiku-latest",
    
    /**
     * LLM model to use for code generation (should be high quality).
     * Default: Claude Sonnet 4.5 for best code quality.
     */
    val generationLlm: String = "claude-sonnet-4-5-20250514",
    
    /**
     * Timeout for context gathering operations.
     */
    val contextGatheringTimeout: Duration = Duration.ofSeconds(30),
    
    /**
     * Temperature for code generation (lower = more deterministic).
     */
    val generationTemperature: Double = 0.3,
    
    /**
     * Temperature for conversational responses (higher = more creative).
     */
    val conversationTemperature: Double = 0.7
)
