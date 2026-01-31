package com.homebrain.agent.domain.embedding

import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddingTest {

    @Nested
    inner class Construction {
        
        @Test
        fun `should create embedding with valid vector`() {
            val vector = FloatArray(768) { it.toFloat() }
            val embedding = Embedding(vector)
            
            assertEquals(768, embedding.vector.size)
        }
        
        @Test
        fun `should reject empty vector`() {
            assertThrows<IllegalArgumentException> {
                Embedding(FloatArray(0))
            }
        }
        
        @Test
        fun `should allow any non-empty dimension`() {
            val vector = FloatArray(256) { 0.5f }
            val embedding = Embedding(vector)
            
            assertEquals(256, embedding.vector.size)
        }
    }
    
    @Nested
    inner class CosineSimilarity {
        
        @Test
        fun `identical vectors should have similarity of 1`() {
            val vector = floatArrayOf(1f, 2f, 3f, 4f)
            val embedding1 = Embedding(vector.copyOf())
            val embedding2 = Embedding(vector.copyOf())
            
            val similarity = embedding1.cosineSimilarity(embedding2)
            
            assertTrue(abs(similarity - 1f) < 0.0001f, "Expected similarity ~1, got $similarity")
        }
        
        @Test
        fun `opposite vectors should have similarity of -1`() {
            val vector1 = floatArrayOf(1f, 0f, 0f)
            val vector2 = floatArrayOf(-1f, 0f, 0f)
            val embedding1 = Embedding(vector1)
            val embedding2 = Embedding(vector2)
            
            val similarity = embedding1.cosineSimilarity(embedding2)
            
            assertTrue(abs(similarity + 1f) < 0.0001f, "Expected similarity ~-1, got $similarity")
        }
        
        @Test
        fun `orthogonal vectors should have similarity of 0`() {
            val vector1 = floatArrayOf(1f, 0f, 0f)
            val vector2 = floatArrayOf(0f, 1f, 0f)
            val embedding1 = Embedding(vector1)
            val embedding2 = Embedding(vector2)
            
            val similarity = embedding1.cosineSimilarity(embedding2)
            
            assertTrue(abs(similarity) < 0.0001f, "Expected similarity ~0, got $similarity")
        }
        
        @Test
        fun `should reject vectors of different dimensions`() {
            val embedding1 = Embedding(FloatArray(768) { 1f })
            val embedding2 = Embedding(FloatArray(512) { 1f })
            
            assertThrows<IllegalArgumentException> {
                embedding1.cosineSimilarity(embedding2)
            }
        }
        
        @Test
        fun `similarity should be symmetric`() {
            val embedding1 = Embedding(floatArrayOf(1f, 2f, 3f))
            val embedding2 = Embedding(floatArrayOf(4f, 5f, 6f))
            
            val sim1to2 = embedding1.cosineSimilarity(embedding2)
            val sim2to1 = embedding2.cosineSimilarity(embedding1)
            
            assertTrue(abs(sim1to2 - sim2to1) < 0.0001f)
        }
    }
    
    @Nested
    inner class Equality {
        
        @Test
        fun `embeddings with same vectors should be equal`() {
            val vector = floatArrayOf(1f, 2f, 3f)
            val embedding1 = Embedding(vector.copyOf())
            val embedding2 = Embedding(vector.copyOf())
            
            assertEquals(embedding1, embedding2)
            assertEquals(embedding1.hashCode(), embedding2.hashCode())
        }
        
        @Test
        fun `embeddings with different vectors should not be equal`() {
            val embedding1 = Embedding(floatArrayOf(1f, 2f, 3f))
            val embedding2 = Embedding(floatArrayOf(4f, 5f, 6f))
            
            assertTrue(embedding1 != embedding2)
        }
    }
    
    @Nested
    inner class PropertyBased {
        
        @Test
        fun `cosine similarity should always be between -1 and 1`() = runBlocking {
            val vectorArb = Arb.list(Arb.float(-100f, 100f), 10..10)
            
            checkAll(100, vectorArb, vectorArb) { v1, v2 ->
                // Skip if all zeros (would cause division by zero)
                if (v1.any { it != 0f } && v2.any { it != 0f }) {
                    val embedding1 = Embedding(v1.toFloatArray())
                    val embedding2 = Embedding(v2.toFloatArray())
                    
                    val similarity = embedding1.cosineSimilarity(embedding2)
                    
                    assertTrue(similarity >= -1.001f && similarity <= 1.001f,
                        "Similarity $similarity out of range [-1, 1]")
                }
            }
        }
        
        @Test
        fun `self-similarity should always be 1 for non-zero vectors`() = runBlocking {
            val vectorArb = Arb.list(Arb.float(0.1f, 100f), 10..10)
            
            checkAll(100, vectorArb) { v ->
                val embedding = Embedding(v.toFloatArray())
                val selfSimilarity = embedding.cosineSimilarity(embedding)
                
                assertTrue(abs(selfSimilarity - 1f) < 0.001f,
                    "Self-similarity should be 1, got $selfSimilarity")
            }
        }
    }
    
    companion object {
        const val CODERANK_DIMENSION = 768
    }
}
