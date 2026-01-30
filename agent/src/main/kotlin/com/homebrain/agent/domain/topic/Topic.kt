package com.homebrain.agent.domain.topic

import java.time.Instant

/**
 * Entity representing a discovered MQTT topic.
 * 
 * Topics are discovered by the engine as devices publish messages.
 * The agent queries the engine to get the list of known topics.
 */
data class Topic(
    val path: TopicPath,
    val lastValue: String? = null,
    val lastSeen: Instant? = null
) {
    /**
     * Checks if this topic contains a keyword in its path.
     */
    fun containsKeyword(keyword: String): Boolean = 
        path.value.contains(keyword, ignoreCase = true)

    /**
     * Gets the device name from the topic path (typically the second segment).
     * E.g., "zigbee2mqtt/living_room_light/state" -> "living_room_light"
     */
    fun deviceName(): String? {
        val segments = path.segments()
        return if (segments.size >= 2) segments[1] else null
    }

    companion object {
        /**
         * Creates a Topic from a path string.
         */
        fun fromPath(path: String): Topic = Topic(TopicPath(path))

        /**
         * Creates a Topic from a path string with a last value.
         */
        fun fromPath(path: String, lastValue: String?): Topic = 
            Topic(TopicPath(path), lastValue)
    }
}
