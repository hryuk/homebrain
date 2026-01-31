package com.homebrain.agent.infrastructure.embedding

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.Shape
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import com.homebrain.agent.domain.embedding.Embedding
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Client for generating code embeddings using CodeRankEmbed-137M via DJL + ONNX Runtime.
 * 
 * CodeRankEmbed is a 137M parameter model optimized for code retrieval tasks.
 * It produces 768-dimensional embeddings with 8192 token context length.
 */
@Component
class CodeRankEmbedClient(
    @Value("\${app.embeddings.model-path:/app/models/coderank-embed}")
    private val modelPath: String,
    
    @Value("\${app.embeddings.max-length:8192}")
    private val maxLength: Int = 8192
) {
    private val logger = KotlinLogging.logger {}
    
    private lateinit var tokenizer: HuggingFaceTokenizer
    private lateinit var model: ZooModel<NDList, NDList>
    private lateinit var manager: NDManager
    
    /** Query prefix required by CodeRankEmbed for search queries */
    private val queryPrefix = "Represent this query for searching relevant code: "
    
    @PostConstruct
    fun initialize() {
        val modelDir = Paths.get(modelPath)
        
        if (!Files.exists(modelDir)) {
            throw IllegalStateException("Model directory not found at $modelPath")
        }
        
        logger.info { "Initializing CodeRankEmbed from $modelPath..." }
        
        try {
            // Initialize NDManager
            manager = NDManager.newBaseManager()
            
            // Load tokenizer
            val tokenizerPath = modelDir.resolve("tokenizer.json")
            if (!Files.exists(tokenizerPath)) {
                throw IllegalStateException("Tokenizer not found at $tokenizerPath")
            }
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath)
            logger.info { "Tokenizer loaded successfully" }
            
            // Load ONNX model
            val criteria = Criteria.builder()
                .setTypes(NDList::class.java, NDList::class.java)
                .optModelPath(modelDir)
                .optEngine("OnnxRuntime")
                .build()
            
            model = criteria.loadModel()
            logger.info { "CodeRankEmbed model loaded successfully (768-dim embeddings)" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize CodeRankEmbed" }
            throw e
        }
    }
    
    /**
     * Check if the model is loaded and ready.
     */
    fun isReady(): Boolean = ::model.isInitialized && ::tokenizer.isInitialized
    
    /**
     * Embed a list of documents (code snippets).
     * Documents do not need a prefix.
     */
    fun embedDocuments(texts: List<String>): List<Embedding> {
        if (!isReady()) {
            throw IllegalStateException("CodeRankEmbed model is not initialized")
        }
        
        return texts.map { text ->
            embedSingle(text)
        }
    }
    
    /**
     * Embed a single document (code snippet).
     */
    fun embedDocument(text: String): Embedding {
        if (!isReady()) {
            throw IllegalStateException("CodeRankEmbed model is not initialized")
        }
        return embedSingle(text)
    }
    
    /**
     * Embed a search query.
     * Automatically adds the required query prefix.
     */
    fun embedQuery(query: String): Embedding {
        if (!isReady()) {
            throw IllegalStateException("CodeRankEmbed model is not initialized")
        }
        val prefixedQuery = queryPrefix + query
        return embedSingle(prefixedQuery)
    }
    
    private fun embedSingle(text: String): Embedding {
        model.newPredictor().use { predictor ->
            // Tokenize
            val encoding = tokenizer.encode(text, true, true)
            val inputIds = encoding.ids
            val attentionMask = encoding.attentionMask
            
            // Truncate if necessary
            val truncatedIds = if (inputIds.size > maxLength) {
                inputIds.take(maxLength).toLongArray()
            } else {
                inputIds
            }
            val truncatedMask = if (attentionMask.size > maxLength) {
                attentionMask.take(maxLength).toLongArray()
            } else {
                attentionMask
            }
            
            manager.newSubManager().use { subManager ->
                // Create input tensors with batch dimension [1, seq_len]
                val shape = Shape(1, truncatedIds.size.toLong())
                val inputIdsTensor = subManager.create(truncatedIds, shape)
                val attentionMaskTensor = subManager.create(truncatedMask, shape)
                
                // Run inference
                val inputs = NDList(inputIdsTensor, attentionMaskTensor)
                val outputs = predictor.predict(inputs)
                
                // Extract embeddings
                val vector = extractEmbeddings(outputs)
                return Embedding(vector)
            }
        }
    }
    
    /**
     * Extract sentence embeddings from model output.
     * Most ONNX exports from sentence-transformers already include pooling.
     */
    private fun extractEmbeddings(outputs: NDList): FloatArray {
        val output = outputs[0]
        val shape = output.shape
        
        return when (shape.dimension()) {
            // [batch_size, hidden_size] - already pooled, just get first batch
            2 -> {
                val batchSize = shape.get(0).toInt()
                val hiddenSize = shape.get(1).toInt()
                val allData = output.toFloatArray()
                // Return first batch (we only have 1)
                allData.copyOfRange(0, hiddenSize)
            }
            // [batch_size, seq_len, hidden_size] - use CLS token (first token)
            3 -> {
                val hiddenSize = shape.get(2).toInt()
                val allData = output.toFloatArray()
                // CLS token is at position 0
                allData.copyOfRange(0, hiddenSize)
            }
            // [hidden_size] - single embedding
            1 -> output.toFloatArray()
            else -> throw IllegalStateException("Unexpected output shape: $shape")
        }
    }
    
    @PreDestroy
    fun cleanup() {
        if (::model.isInitialized) {
            logger.info { "Closing CodeRankEmbed model..." }
            model.close()
        }
        if (::manager.isInitialized) {
            manager.close()
        }
    }
}
