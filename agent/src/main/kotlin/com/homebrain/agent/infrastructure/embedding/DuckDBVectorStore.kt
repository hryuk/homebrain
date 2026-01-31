package com.homebrain.agent.infrastructure.embedding

import com.homebrain.agent.domain.embedding.*
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

/**
 * DuckDB-based vector store for code embeddings.
 * Uses DuckDB's VSS (Vector Similarity Search) extension for efficient similarity search.
 */
@Component
class DuckDBVectorStore(
    @Value("\${app.embeddings.db-path:/app/data/embeddings.duckdb}")
    private val dbPath: String,
    
    @Value("\${app.embeddings.dimension:768}")
    private val dimension: Int = Embedding.CODERANK_DIMENSION
) : EmbeddingRepository {
    
    private val logger = KotlinLogging.logger {}
    private lateinit var connection: Connection
    
    @PostConstruct
    fun initialize() {
        logger.info { "Initializing DuckDB vector store at $dbPath..." }
        
        // Ensure parent directory exists
        val dbDir = Paths.get(dbPath).parent
        if (dbDir != null && !Files.exists(dbDir)) {
            Files.createDirectories(dbDir)
        }
        
        // Connect to DuckDB
        connection = DriverManager.getConnection("jdbc:duckdb:$dbPath")
        
        connection.createStatement().use { stmt ->
            // Install and load VSS extension for array_cosine_similarity function
            stmt.execute("INSTALL vss")
            stmt.execute("LOAD vss")
            
            // Create table for code embeddings
            // Note: Not using HNSW index as experimental persistence causes WAL replay issues
            // Brute-force search is fast enough for < 1000 automations
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS code_embeddings (
                    id VARCHAR PRIMARY KEY,
                    type VARCHAR NOT NULL,
                    name VARCHAR NOT NULL,
                    source_code TEXT NOT NULL,
                    embedding FLOAT[$dimension]
                )
            """)
        }
        
        logger.info { "DuckDB vector store initialized successfully" }
    }
    
    /**
     * Check if the store is ready.
     */
    fun isReady(): Boolean = ::connection.isInitialized && !connection.isClosed
    
    override fun save(indexed: IndexedCode) {
        if (indexed.embedding == null) {
            throw IllegalArgumentException("Cannot save IndexedCode without embedding")
        }
        
        // Delete existing entry first (DuckDB doesn't support UPDATE with array columns)
        connection.prepareStatement("DELETE FROM code_embeddings WHERE id = ?").use { stmt ->
            stmt.setString(1, indexed.id)
            stmt.executeUpdate()
        }
        
        // Format embedding as DuckDB array literal
        val embeddingLiteral = indexed.embedding.vector.joinToString(", ", "[", "]")
        
        // Insert new entry
        val sql = """
            INSERT INTO code_embeddings (id, type, name, source_code, embedding)
            VALUES (?, ?, ?, ?, $embeddingLiteral::FLOAT[$dimension])
        """
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, indexed.id)
            stmt.setString(2, indexed.type.name)
            stmt.setString(3, indexed.name)
            stmt.setString(4, indexed.sourceCode)
            stmt.executeUpdate()
        }
        
        logger.debug { "Saved embedding for ${indexed.id}" }
    }
    
    override fun delete(id: String) {
        connection.prepareStatement("DELETE FROM code_embeddings WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            val deleted = stmt.executeUpdate()
            if (deleted > 0) {
                logger.debug { "Deleted embedding for $id" }
            }
        }
    }
    
    override fun findById(id: String): IndexedCode? {
        val sql = "SELECT id, type, name, source_code, embedding FROM code_embeddings WHERE id = ?"
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return extractIndexedCode(rs)
                }
            }
        }
        return null
    }
    
    override fun findAll(): List<IndexedCode> {
        val results = mutableListOf<IndexedCode>()
        
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, type, name, source_code, embedding FROM code_embeddings").use { rs ->
                while (rs.next()) {
                    results.add(extractIndexedCode(rs))
                }
            }
        }
        
        return results
    }
    
    override fun searchSimilar(embedding: Embedding, topK: Int): List<CodeSearchResult> {
        val vectorLiteral = embedding.vector.joinToString(", ", "[", "]")
        
        val sql = """
            SELECT 
                id, 
                type, 
                name, 
                source_code,
                array_cosine_similarity(embedding, $vectorLiteral::FLOAT[$dimension]) as similarity
            FROM code_embeddings
            WHERE embedding IS NOT NULL
            ORDER BY similarity DESC
            LIMIT ?
        """
        
        val results = mutableListOf<CodeSearchResult>()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, topK)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val similarity = rs.getFloat("similarity")
                    val clampedSimilarity = similarity.coerceIn(0f, 1f)
                    
                    results.add(
                        CodeSearchResult(
                            id = rs.getString("id"),
                            type = CodeType.valueOf(rs.getString("type")),
                            name = rs.getString("name"),
                            sourceCode = rs.getString("source_code"),
                            similarity = clampedSimilarity
                        )
                    )
                }
            }
        }
        
        return results
    }
    
    override fun getAllIds(): Set<String> {
        val ids = mutableSetOf<String>()
        
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id FROM code_embeddings").use { rs ->
                while (rs.next()) {
                    ids.add(rs.getString("id"))
                }
            }
        }
        
        return ids
    }
    
    override fun isEmpty(): Boolean {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM code_embeddings").use { rs ->
                if (rs.next()) {
                    return rs.getInt(1) == 0
                }
            }
        }
        return true
    }
    
    /**
     * Get the count of indexed items.
     */
    fun count(): Int {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM code_embeddings").use { rs ->
                if (rs.next()) {
                    return rs.getInt(1)
                }
            }
        }
        return 0
    }
    
    private fun extractIndexedCode(rs: java.sql.ResultSet): IndexedCode {
        val embeddingArray = rs.getArray("embedding")
        val embedding = if (embeddingArray != null) {
            // DuckDB returns Object[] containing Float values
            val objArray = embeddingArray.array as Array<*>
            val floatArray = FloatArray(objArray.size) { i ->
                (objArray[i] as Number).toFloat()
            }
            Embedding(floatArray)
        } else {
            null
        }
        
        return IndexedCode(
            id = rs.getString("id"),
            type = CodeType.valueOf(rs.getString("type")),
            name = rs.getString("name"),
            sourceCode = rs.getString("source_code"),
            embedding = embedding
        )
    }
    
    @PreDestroy
    fun cleanup() {
        if (::connection.isInitialized && !connection.isClosed) {
            logger.info { "Closing DuckDB connection..." }
            connection.close()
        }
    }
}
