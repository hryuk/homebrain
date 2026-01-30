package com.homebrain.agent.domain.commit

import java.time.Instant

/**
 * Value object representing a git commit.
 */
data class Commit(
    val hash: String,
    val message: String,
    val author: String,
    val timestamp: Instant
) {
    init {
        require(hash.isNotBlank()) { "Commit hash cannot be blank" }
    }

    /**
     * Gets a shortened version of the commit hash (first 7 characters).
     */
    fun shortHash(): String = hash.take(7)

    override fun toString(): String = "${shortHash()} - $message"
}
