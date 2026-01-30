package com.homebrain.agent.domain.library

/**
 * Represents the global state schema showing which automations
 * have write permissions to which global state keys.
 */
data class GlobalStateSchema(
    val keyPatterns: Map<String, List<String>>
) {
    /**
     * Get all automations that can write to a specific key.
     */
    fun getWritersForKey(key: String): List<String> {
        val writers = mutableListOf<String>()
        for ((pattern, automationIds) in keyPatterns) {
            if (matchesPattern(pattern, key)) {
                writers.addAll(automationIds)
            }
        }
        return writers.distinct()
    }

    /**
     * Check if a pattern matches a key (supports * wildcard).
     */
    private fun matchesPattern(pattern: String, key: String): Boolean {
        if (pattern.endsWith("*")) {
            val prefix = pattern.substring(0, pattern.length - 1)
            return key.startsWith(prefix)
        }
        return pattern == key
    }

    companion object {
        fun empty(): GlobalStateSchema {
            return GlobalStateSchema(emptyMap())
        }

        fun fromMap(keyPatterns: Map<String, List<String>>): GlobalStateSchema {
            return GlobalStateSchema(keyPatterns)
        }
    }
}
