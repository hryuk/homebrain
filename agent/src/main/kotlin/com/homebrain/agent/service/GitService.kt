package com.homebrain.agent.service

import com.homebrain.agent.dto.CommitInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import jakarta.annotation.PostConstruct

private val logger = KotlinLogging.logger {}

@Service
class GitService(
    @Value("\${app.automations.path}")
    private val automationsPath: String
) {
    private lateinit var git: Git
    private lateinit var repoPath: Path

    @PostConstruct
    fun init() {
        repoPath = Path.of(automationsPath)

        // Create directory if it doesn't exist
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
        Files.writeString(file, content)
        logger.debug { "Wrote file: $filename" }
    }

    fun stageFile(filename: String) {
        git.add().addFilepattern(filename).call()
        logger.debug { "Staged file: $filename" }
    }

    fun commit(message: String): String {
        val author = PersonIdent("Homebrain Agent", "agent@homebrain.local")
        val commit = git.commit()
            .setMessage(message)
            .setAuthor(author)
            .setCommitter(author)
            .call()
        val hash = commit.id.name
        logger.info { "Created commit: $hash - $message" }
        return hash
    }

    fun writeAndCommit(filename: String, content: String, message: String): String {
        writeFile(filename, content)
        stageFile(filename)
        return commit(message)
    }

    fun deleteFile(filename: String) {
        val file = repoPath.resolve(filename)
        if (Files.exists(file)) {
            Files.delete(file)
            git.rm().addFilepattern(filename).call()
            logger.debug { "Deleted file: $filename" }
        }
    }

    fun readFile(filename: String): String {
        val file = repoPath.resolve(filename)
        return Files.readString(file)
    }

    fun listFiles(): List<String> {
        return repoPath.toFile()
            .listFiles { file -> file.extension == "star" }
            ?.map { it.name }
            ?: emptyList()
    }

    fun getHistory(limit: Int = 50): List<CommitInfo> {
        return try {
            git.log()
                .setMaxCount(limit)
                .call()
                .map { commit ->
                    CommitInfo(
                        hash = commit.id.name,
                        message = commit.shortMessage,
                        author = commit.authorIdent.name,
                        date = Instant.ofEpochSecond(commit.commitTime.toLong())
                    )
                }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get git history" }
            emptyList()
        }
    }
}
