package com.homebrain.agent.domain.embedding

/**
 * Repository port for storing and searching code embeddings.
 */
interface EmbeddingRepository {
    
    /**
     * Save or update an indexed code entry.
     */
    fun save(indexed: IndexedCode)
    
    /**
     * Delete an indexed code entry by ID.
     */
    fun delete(id: String)
    
    /**
     * Find an indexed code entry by ID.
     */
    fun findById(id: String): IndexedCode?
    
    /**
     * Get all indexed code entries.
     */
    fun findAll(): List<IndexedCode>
    
    /**
     * Search for similar code using cosine similarity.
     * 
     * @param embedding The query embedding to search for
     * @param topK Maximum number of results to return
     * @return List of search results ordered by similarity (highest first)
     */
    fun searchSimilar(embedding: Embedding, topK: Int): List<CodeSearchResult>
    
    /**
     * Get all indexed IDs (for sync purposes).
     */
    fun getAllIds(): Set<String>
    
    /**
     * Check if the repository has any entries.
     */
    fun isEmpty(): Boolean
}
