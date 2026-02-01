package com.homebrain.agent.application

import com.homebrain.agent.domain.embedding.*
import com.homebrain.agent.infrastructure.embedding.CodeRankEmbedClient
import com.homebrain.agent.infrastructure.embedding.DuckDBVectorStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Service for managing code embeddings.
 * 
 * Indexes automations and library modules from the filesystem,
 * computes embeddings using CodeRankEmbed, and stores them in DuckDB.
 */
@Service
class CodeEmbeddingService(
    private val embeddingClient: CodeRankEmbedClient,
    private val vectorStore: DuckDBVectorStore,
    
    @Value("\${app.automations.path:/app/automations}")
    private val automationsPath: String,
    
    @Value("\${app.embeddings.similarity-threshold:0.7}")
    private val similarityThreshold: Float = 0.7f
) {
    private val logger = KotlinLogging.logger {}
    
    /**
     * Check if the embedding service is ready.
     */
    fun isReady(): Boolean = embeddingClient.isReady() && vectorStore.isReady()
    
    /**
     * Index a single automation by name.
     * Runs asynchronously to avoid blocking API responses.
     */
    @Async("embeddingExecutor")
    fun indexAutomation(name: String, code: String) {
        if (!isReady()) {
            logger.warn { "Embedding service not ready, skipping indexing for automation: $name" }
            return
        }
        
        logger.info { "Indexing automation: $name" }
        
        try {
            val embedding = embeddingClient.embedDocument(code)
            val indexed = IndexedCode.forAutomation(name, code, embedding)
            vectorStore.save(indexed)
            logger.debug { "Successfully indexed automation: $name" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to index automation: $name" }
        }
    }
    
    /**
     * Index a single library module by name.
     * Runs asynchronously to avoid blocking API responses.
     */
    @Async("embeddingExecutor")
    fun indexLibrary(name: String, code: String) {
        if (!isReady()) {
            logger.warn { "Embedding service not ready, skipping indexing for library: $name" }
            return
        }
        
        logger.info { "Indexing library: $name" }
        
        try {
            val embedding = embeddingClient.embedDocument(code)
            val indexed = IndexedCode.forLibrary(name, code, embedding)
            vectorStore.save(indexed)
            logger.debug { "Successfully indexed library: $name" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to index library: $name" }
        }
    }
    
    /**
     * Remove an automation from the index.
     * Runs asynchronously to avoid blocking API responses.
     */
    @Async("embeddingExecutor")
    fun removeAutomation(name: String) {
        vectorStore.delete(IndexedCode.automationId(name))
        logger.debug { "Removed automation from index: $name" }
    }
    
    /**
     * Remove a library from the index.
     * Runs asynchronously to avoid blocking API responses.
     */
    @Async("embeddingExecutor")
    fun removeLibrary(name: String) {
        vectorStore.delete(IndexedCode.libraryId(name))
        logger.debug { "Removed library from index: $name" }
    }
    
    /**
     * Search for similar code using a natural language query.
     * 
     * @param query Natural language description of functionality to search for
     * @param topK Maximum number of results to return
     * @return List of search results ordered by similarity (highest first)
     */
    fun search(query: String, topK: Int = 5): List<CodeSearchResult> {
        if (!isReady()) {
            logger.warn { "Embedding service not ready, cannot search" }
            return emptyList()
        }
        
        logger.debug { "Searching for similar code: '$query'" }
        
        val queryEmbedding = embeddingClient.embedQuery(query)
        return vectorStore.searchSimilar(queryEmbedding, topK)
    }
    
    /**
     * Search for similar code with a minimum similarity threshold.
     */
    fun searchWithThreshold(query: String, topK: Int = 5, threshold: Float = similarityThreshold): List<CodeSearchResult> {
        return search(query, topK).filter { it.similarity >= threshold }
    }
    
    /**
     * Sync the embedding index with the filesystem.
     * Scans the automations directory and indexes any new or changed files.
     */
    fun syncWithFilesystem() {
        if (!isReady()) {
            logger.warn { "Embedding service not ready, skipping sync" }
            return
        }
        
        logger.info { "Syncing embeddings with filesystem..." }
        
        val automationsDir = Paths.get(automationsPath)
        if (!Files.exists(automationsDir)) {
            logger.warn { "Automations directory not found: $automationsPath" }
            return
        }
        
        val indexedIds = vectorStore.getAllIds().toMutableSet()
        var automationsIndexed = 0
        var librariesIndexed = 0
        
        // Index automations (*.star files in root, excluding lib/)
        Files.list(automationsDir)
            .filter { Files.isRegularFile(it) && it.extension == "star" }
            .forEach { file ->
                val name = file.nameWithoutExtension
                val id = IndexedCode.automationId(name)
                
                try {
                    val code = file.readText()
                    val existingEntry = vectorStore.findById(id)
                    
                    // Re-index if new or code changed
                    if (existingEntry == null || existingEntry.sourceCode != code) {
                        indexAutomation(name, code)
                        automationsIndexed++
                    }
                    indexedIds.remove(id)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to read automation file: $file" }
                }
            }
        
        // Index libraries (*.lib.star files in lib/)
        val libDir = automationsDir.resolve("lib")
        if (Files.exists(libDir) && Files.isDirectory(libDir)) {
            Files.list(libDir)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".lib.star") }
                .forEach { file ->
                    val name = file.fileName.toString().removeSuffix(".lib.star")
                    val id = IndexedCode.libraryId(name)
                    
                    try {
                        val code = file.readText()
                        val existingEntry = vectorStore.findById(id)
                        
                        // Re-index if new or code changed
                        if (existingEntry == null || existingEntry.sourceCode != code) {
                            indexLibrary(name, code)
                            librariesIndexed++
                        }
                        indexedIds.remove(id)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to read library file: $file" }
                    }
                }
        }
        
        // Remove entries that no longer exist on filesystem
        indexedIds.forEach { id ->
            logger.info { "Removing stale index entry: $id" }
            vectorStore.delete(id)
        }
        
        val totalCount = vectorStore.count()
        logger.info { 
            "Sync complete: $automationsIndexed automations indexed, " +
            "$librariesIndexed libraries indexed, " +
            "${indexedIds.size} stale entries removed, " +
            "$totalCount total entries"
        }
    }
    
    /**
     * Force reindex of all files.
     */
    fun reindexAll() {
        if (!isReady()) {
            logger.warn { "Embedding service not ready, cannot reindex" }
            return
        }
        
        logger.info { "Force reindexing all files..." }
        
        val automationsDir = Paths.get(automationsPath)
        if (!Files.exists(automationsDir)) {
            logger.warn { "Automations directory not found: $automationsPath" }
            return
        }
        
        var automationsIndexed = 0
        var librariesIndexed = 0
        
        // Index automations
        Files.list(automationsDir)
            .filter { Files.isRegularFile(it) && it.extension == "star" }
            .forEach { file ->
                val name = file.nameWithoutExtension
                try {
                    val code = file.readText()
                    indexAutomation(name, code)
                    automationsIndexed++
                } catch (e: Exception) {
                    logger.error(e) { "Failed to index automation: $file" }
                }
            }
        
        // Index libraries
        val libDir = automationsDir.resolve("lib")
        if (Files.exists(libDir) && Files.isDirectory(libDir)) {
            Files.list(libDir)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".lib.star") }
                .forEach { file ->
                    val name = file.fileName.toString().removeSuffix(".lib.star")
                    try {
                        val code = file.readText()
                        indexLibrary(name, code)
                        librariesIndexed++
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to index library: $file" }
                    }
                }
        }
        
        logger.info { "Reindex complete: $automationsIndexed automations, $librariesIndexed libraries" }
    }
    
    /**
     * Get the number of indexed items.
     */
    fun getIndexedCount(): Int = if (isReady()) vectorStore.count() else 0
}
