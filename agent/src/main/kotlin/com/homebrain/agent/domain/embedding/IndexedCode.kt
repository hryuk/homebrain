package com.homebrain.agent.domain.embedding

/**
 * Entity representing code that has been indexed for semantic search.
 * 
 * @property id Unique identifier (e.g., "automation:motion_light" or "library:timers")
 * @property type Whether this is an automation or library module
 * @property name Human-readable name (e.g., "motion_light" or "timers")
 * @property sourceCode The complete source code
 * @property embedding The computed embedding vector (null if not yet computed)
 */
data class IndexedCode(
    val id: String,
    val type: CodeType,
    val name: String,
    val sourceCode: String,
    val embedding: Embedding? = null
) {
    init {
        require(id.isNotBlank()) { "IndexedCode id cannot be blank" }
        require(name.isNotBlank()) { "IndexedCode name cannot be blank" }
        require(sourceCode.isNotBlank()) { "IndexedCode sourceCode cannot be blank" }
    }
    
    /**
     * Create a copy with the embedding set.
     */
    fun withEmbedding(embedding: Embedding): IndexedCode = copy(embedding = embedding)
    
    companion object {
        /**
         * Create an ID for an automation.
         */
        fun automationId(name: String): String = "automation:$name"
        
        /**
         * Create an ID for a library module.
         */
        fun libraryId(name: String): String = "library:$name"
        
        /**
         * Create an IndexedCode for an automation.
         */
        fun forAutomation(name: String, sourceCode: String, embedding: Embedding? = null): IndexedCode =
            IndexedCode(
                id = automationId(name),
                type = CodeType.AUTOMATION,
                name = name,
                sourceCode = sourceCode,
                embedding = embedding
            )
        
        /**
         * Create an IndexedCode for a library module.
         */
        fun forLibrary(name: String, sourceCode: String, embedding: Embedding? = null): IndexedCode =
            IndexedCode(
                id = libraryId(name),
                type = CodeType.LIBRARY,
                name = name,
                sourceCode = sourceCode,
                embedding = embedding
            )
    }
}
