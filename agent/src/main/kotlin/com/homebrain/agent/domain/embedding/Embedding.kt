package com.homebrain.agent.domain.embedding

/**
 * Value object representing a code embedding vector.
 * CodeRankEmbed produces 768-dimensional vectors.
 */
data class Embedding(val vector: FloatArray) {
    
    init {
        require(vector.isNotEmpty()) { "Embedding vector cannot be empty" }
    }
    
    /**
     * Compute cosine similarity with another embedding.
     * Returns a value between -1 and 1, where 1 means identical.
     */
    fun cosineSimilarity(other: Embedding): Float {
        require(vector.size == other.vector.size) { 
            "Embeddings must have same dimension: ${vector.size} vs ${other.vector.size}" 
        }
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in vector.indices) {
            dotProduct += vector[i] * other.vector[i]
            normA += vector[i] * vector[i]
            normB += other.vector[i] * other.vector[i]
        }
        
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Embedding
        return vector.contentEquals(other.vector)
    }
    
    override fun hashCode(): Int = vector.contentHashCode()
    
    companion object {
        /** Expected dimension for CodeRankEmbed-137M */
        const val CODERANK_DIMENSION = 768
    }
}
