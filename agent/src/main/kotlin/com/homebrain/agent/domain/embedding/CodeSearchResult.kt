package com.homebrain.agent.domain.embedding

/**
 * Result of a semantic code search.
 * 
 * @property id Unique identifier of the matched code
 * @property type Whether this is an automation or library
 * @property name Human-readable name
 * @property sourceCode The complete source code
 * @property similarity Cosine similarity score (0.0 to 1.0, higher = more similar)
 */
data class CodeSearchResult(
    val id: String,
    val type: CodeType,
    val name: String,
    val sourceCode: String,
    val similarity: Float
) {
    init {
        require(similarity in 0f..1f) { "Similarity must be between 0 and 1, got: $similarity" }
    }
    
    companion object {
        /**
         * Create from an IndexedCode and similarity score.
         */
        fun from(indexed: IndexedCode, similarity: Float): CodeSearchResult =
            CodeSearchResult(
                id = indexed.id,
                type = indexed.type,
                name = indexed.name,
                sourceCode = indexed.sourceCode,
                similarity = similarity
            )
    }
}
