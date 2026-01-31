package com.homebrain.agent.domain.embedding

/**
 * Type of indexed code in the embedding store.
 */
enum class CodeType {
    /** A complete automation script (.star file) */
    AUTOMATION,
    
    /** A library module (.lib.star file) */
    LIBRARY
}
