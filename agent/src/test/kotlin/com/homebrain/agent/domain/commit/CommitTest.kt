package com.homebrain.agent.domain.commit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class CommitTest {

    private val sampleTimestamp = Instant.parse("2024-01-15T10:30:00Z")

    @Nested
    inner class Construction {
        @Test
        fun `should create valid commit`() {
            val commit = Commit(
                hash = "abc123def456",
                message = "Add new automation",
                author = "homebrain",
                timestamp = sampleTimestamp
            )
            assertEquals("abc123def456", commit.hash)
            assertEquals("Add new automation", commit.message)
            assertEquals("homebrain", commit.author)
            assertEquals(sampleTimestamp, commit.timestamp)
        }

        @Test
        fun `should reject blank hash`() {
            val exception = assertThrows<IllegalArgumentException> {
                Commit(
                    hash = "",
                    message = "Add new automation",
                    author = "homebrain",
                    timestamp = sampleTimestamp
                )
            }
            assertEquals("Commit hash cannot be blank", exception.message)
        }

        @Test
        fun `should reject whitespace-only hash`() {
            val exception = assertThrows<IllegalArgumentException> {
                Commit(
                    hash = "   ",
                    message = "Add new automation",
                    author = "homebrain",
                    timestamp = sampleTimestamp
                )
            }
            assertEquals("Commit hash cannot be blank", exception.message)
        }

        @Test
        fun `should allow empty message`() {
            val commit = Commit(
                hash = "abc123",
                message = "",
                author = "homebrain",
                timestamp = sampleTimestamp
            )
            assertEquals("", commit.message)
        }

        @Test
        fun `should allow empty author`() {
            val commit = Commit(
                hash = "abc123",
                message = "Test",
                author = "",
                timestamp = sampleTimestamp
            )
            assertEquals("", commit.author)
        }
    }

    @Nested
    inner class ShortHash {
        @Test
        fun `should return first 7 characters`() {
            val commit = Commit(
                hash = "abc123def456789",
                message = "Test",
                author = "test",
                timestamp = sampleTimestamp
            )
            assertEquals("abc123d", commit.shortHash())
        }

        @Test
        fun `should return full hash if shorter than 7 characters`() {
            val commit = Commit(
                hash = "abc",
                message = "Test",
                author = "test",
                timestamp = sampleTimestamp
            )
            assertEquals("abc", commit.shortHash())
        }

        @Test
        fun `should return exactly 7 characters for standard hash`() {
            val commit = Commit(
                hash = "1234567890abcdef",
                message = "Test",
                author = "test",
                timestamp = sampleTimestamp
            )
            assertEquals(7, commit.shortHash().length)
        }
    }

    @Nested
    inner class ToStringTest {
        @Test
        fun `should format as short hash dash message`() {
            val commit = Commit(
                hash = "abc123def456",
                message = "Add light controller",
                author = "homebrain",
                timestamp = sampleTimestamp
            )
            assertEquals("abc123d - Add light controller", commit.toString())
        }

        @Test
        fun `should handle empty message`() {
            val commit = Commit(
                hash = "abc123def456",
                message = "",
                author = "homebrain",
                timestamp = sampleTimestamp
            )
            assertEquals("abc123d - ", commit.toString())
        }
    }

    @Nested
    inner class Equality {
        @Test
        fun `commits with same properties should be equal`() {
            val commit1 = Commit("hash1", "message", "author", sampleTimestamp)
            val commit2 = Commit("hash1", "message", "author", sampleTimestamp)
            assertEquals(commit1, commit2)
        }

        @Test
        fun `commits with different hashes should not be equal`() {
            val commit1 = Commit("hash1", "message", "author", sampleTimestamp)
            val commit2 = Commit("hash2", "message", "author", sampleTimestamp)
            assertNotEquals(commit1, commit2)
        }

        @Test
        fun `commits with different timestamps should not be equal`() {
            val commit1 = Commit("hash1", "message", "author", sampleTimestamp)
            val commit2 = Commit("hash1", "message", "author", sampleTimestamp.plusSeconds(1))
            assertNotEquals(commit1, commit2)
        }
    }
}
