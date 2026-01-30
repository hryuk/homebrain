package com.homebrain.agent.infrastructure.persistence

import com.homebrain.agent.domain.commit.Commit
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Low-level Git operations for the automations repository.
 * 
 * This class handles the mechanics of git operations (init, add, commit, etc.)
 * while the repository implementations handle domain logic.
 */
@Component
class GitOperations(
    @Value("\${app.automations.path}")
    private val automationsPath: String
) {
    private lateinit var git: Git
    private lateinit var _repoPath: Path
    val repoPath: Path
        get() = _repoPath

    @PostConstruct
    fun init() {
        _repoPath = Path.of(automationsPath)

        if (!Files.exists(repoPath)) {
            Files.createDirectories(repoPath)
            logger.info { "Created automations directory: $automationsPath" }
        }

        val gitDir = repoPath.resolve(".git")
        git = if (Files.exists(gitDir)) {
            logger.info { "Opening existing git repository at $automationsPath" }
            Git.open(repoPath.toFile())
        } else {
            logger.info { "Initializing new git repository at $automationsPath" }
            Git.init().setDirectory(repoPath.toFile()).call()
        }
    }

    fun writeFile(filename: String, content: String) {
        val file = repoPath.resolve(filename)
        // Ensure parent directories exist (e.g., lib/)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        logger.debug { "Wrote file: $filename" }
    }

    fun readFile(filename: String): String {
        val file = repoPath.resolve(filename)
        return Files.readString(file)
    }

    fun fileExists(filename: String): Boolean {
        return Files.exists(repoPath.resolve(filename))
    }

    fun deleteFile(filename: String) {
        val file = repoPath.resolve(filename)
        if (Files.exists(file)) {
            Files.delete(file)
            git.rm().addFilepattern(filename).call()
            logger.debug { "Deleted file: $filename" }
        }
    }

    fun listFiles(extension: String): List<String> {
        return repoPath.toFile()
            .listFiles { file -> file.extension == extension }
            ?.map { it.name }
            ?: emptyList()
    }

    fun stageFile(filename: String) {
        git.add().addFilepattern(filename).call()
        logger.debug { "Staged file: $filename" }
    }

    fun commit(message: String): Commit {
        val author = PersonIdent("Homebrain Agent", "agent@homebrain.local")
        val gitCommit = git.commit()
            .setMessage(message)
            .setAuthor(author)
            .setCommitter(author)
            .call()
        
        val commit = Commit(
            hash = gitCommit.id.name,
            message = message,
            author = author.name,
            timestamp = Instant.ofEpochSecond(gitCommit.commitTime.toLong())
        )
        logger.info { "Created commit: ${commit.shortHash()} - $message" }
        return commit
    }

    fun getHistory(limit: Int = 50): List<Commit> {
        return try {
            git.log()
                .setMaxCount(limit)
                .call()
                .map { gitCommit ->
                    Commit(
                        hash = gitCommit.id.name,
                        message = gitCommit.shortMessage,
                        author = gitCommit.authorIdent.name,
                        timestamp = Instant.ofEpochSecond(gitCommit.commitTime.toLong())
                    )
                }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get git history" }
            emptyList()
        }
    }
}
