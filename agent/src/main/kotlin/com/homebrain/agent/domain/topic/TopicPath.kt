package com.homebrain.agent.domain.topic

/**
 * Value object representing an MQTT topic path.
 */
@JvmInline
value class TopicPath(val value: String) {
    init {
        require(value.isNotBlank()) { "Topic path cannot be blank" }
    }

    /**
     * Checks if this is a wildcard topic pattern.
     */
    fun isWildcard(): Boolean = value.contains("+") || value.contains("#")

    /**
     * Gets the topic segments (split by /).
     */
    fun segments(): List<String> = value.split("/")

    /**
     * Gets the root segment of the topic (e.g., "zigbee2mqtt" from "zigbee2mqtt/device/state").
     */
    fun root(): String = segments().firstOrNull() ?: value

    /**
     * Checks if this topic matches a given pattern (supporting + and # wildcards).
     */
    fun matches(pattern: TopicPath): Boolean {
        if (!pattern.isWildcard()) {
            return this.value == pattern.value
        }

        val topicParts = this.segments()
        val patternParts = pattern.segments()

        var ti = 0
        var pi = 0

        while (pi < patternParts.size && ti < topicParts.size) {
            when (patternParts[pi]) {
                "#" -> return true // # matches everything remaining
                "+" -> {
                    ti++
                    pi++
                }
                else -> {
                    if (patternParts[pi] != topicParts[ti]) return false
                    ti++
                    pi++
                }
            }
        }

        return ti == topicParts.size && pi == patternParts.size
    }

    override fun toString(): String = value
}
