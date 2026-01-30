package com.homebrain.agent.domain.conversation

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CodeProposalTest {

    private val sampleAutomation = FileProposal.automation(
        code = "def on_message(t, p, ctx): pass\nconfig = {}",
        filename = "test_automation.star"
    )

    private val sampleLibrary = FileProposal.library(
        code = "def helper(ctx): return True",
        filename = "lib/helpers.lib.star"
    )

    @Nested
    inner class Construction {
        @Test
        fun `should create proposal with single automation file`() {
            val proposal = CodeProposal(
                summary = "Test automation",
                files = listOf(sampleAutomation)
            )
            assertEquals("Test automation", proposal.summary)
            assertEquals(1, proposal.files.size)
            assertEquals(sampleAutomation, proposal.files[0])
        }

        @Test
        fun `should create proposal with library and automation`() {
            val proposal = CodeProposal(
                summary = "Library and automation together",
                files = listOf(sampleLibrary, sampleAutomation)
            )
            assertEquals(2, proposal.files.size)
            assertTrue(proposal.files[0].isLibrary())
            assertTrue(proposal.files[1].isAutomation())
        }

        @Test
        fun `should reject blank summary`() {
            val exception = assertThrows<IllegalArgumentException> {
                CodeProposal("", listOf(sampleAutomation))
            }
            assertEquals("Summary cannot be blank", exception.message)
        }

        @Test
        fun `should reject whitespace-only summary`() {
            val exception = assertThrows<IllegalArgumentException> {
                CodeProposal("   \n\t", listOf(sampleAutomation))
            }
            assertEquals("Summary cannot be blank", exception.message)
        }

        @Test
        fun `should reject empty files list`() {
            val exception = assertThrows<IllegalArgumentException> {
                CodeProposal("Summary", emptyList())
            }
            assertEquals("At least one file is required", exception.message)
        }
    }

    @Nested
    inner class HelperMethods {
        @Test
        fun `getLibraries should return only library files`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleLibrary, sampleAutomation)
            )
            val libraries = proposal.getLibraries()
            assertEquals(1, libraries.size)
            assertTrue(libraries[0].isLibrary())
        }

        @Test
        fun `getLibraries should return empty list when no libraries`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleAutomation)
            )
            assertTrue(proposal.getLibraries().isEmpty())
        }

        @Test
        fun `getAutomations should return only automation files`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleLibrary, sampleAutomation)
            )
            val automations = proposal.getAutomations()
            assertEquals(1, automations.size)
            assertTrue(automations[0].isAutomation())
        }

        @Test
        fun `hasLibrary should return true when library exists`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleLibrary, sampleAutomation)
            )
            assertTrue(proposal.hasLibrary())
        }

        @Test
        fun `hasLibrary should return false when no library`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleAutomation)
            )
            assertFalse(proposal.hasLibrary())
        }

        @Test
        fun `isSingleFile should return true for single file`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleAutomation)
            )
            assertTrue(proposal.isSingleFile())
        }

        @Test
        fun `isSingleFile should return false for multiple files`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleLibrary, sampleAutomation)
            )
            assertFalse(proposal.isSingleFile())
        }

        @Test
        fun `primaryFile should return automation when both exist`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleLibrary, sampleAutomation)
            )
            assertEquals(sampleAutomation, proposal.primaryFile())
        }

        @Test
        fun `primaryFile should return library when only library exists`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleLibrary)
            )
            assertEquals(sampleLibrary, proposal.primaryFile())
        }

        @Test
        fun `primaryFile should return first file when no automation`() {
            val lib1 = FileProposal.library("code1", "lib/a.lib.star")
            val lib2 = FileProposal.library("code2", "lib/b.lib.star")
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(lib1, lib2)
            )
            assertEquals(lib1, proposal.primaryFile())
        }
    }

    @Nested
    inner class BackwardsCompatibility {
        @Test
        fun `single file getters should work for legacy compatibility`() {
            val proposal = CodeProposal(
                summary = "Test automation summary",
                files = listOf(sampleAutomation)
            )
            
            // These provide backwards compatibility for single-file usage
            assertEquals(sampleAutomation.code, proposal.code)
            assertEquals(sampleAutomation.filename, proposal.filename)
        }

        @Test
        fun `single file getters should return primary file for multi-file`() {
            val proposal = CodeProposal(
                summary = "Test",
                files = listOf(sampleLibrary, sampleAutomation)
            )
            
            // Should return the automation (primary) file
            assertEquals(sampleAutomation.code, proposal.code)
            assertEquals(sampleAutomation.filename, proposal.filename)
        }
    }

    @Nested
    inner class FactoryMethods {
        @Test
        fun `singleAutomation should create proposal with one automation`() {
            val proposal = CodeProposal.singleAutomation(
                code = "def test(): pass",
                filename = "test.star",
                summary = "A test automation"
            )
            assertTrue(proposal.isSingleFile())
            assertTrue(proposal.files[0].isAutomation())
            assertEquals("A test automation", proposal.summary)
        }

        @Test
        fun `withLibrary should create proposal with library and automation`() {
            val proposal = CodeProposal.withLibrary(
                libraryCode = "def helper(): pass",
                libraryFilename = "lib/helpers.lib.star",
                automationCode = "def on_message(t, p, ctx): ctx.lib.helpers.helper()",
                automationFilename = "use_helper.star",
                summary = "Automation with library"
            )
            assertEquals(2, proposal.files.size)
            assertTrue(proposal.hasLibrary())
            assertEquals("lib/helpers.lib.star", proposal.getLibraries()[0].filename)
            assertEquals("use_helper.star", proposal.getAutomations()[0].filename)
        }
    }

    @Nested
    inner class PropertyBasedTests {
        private val nonBlankStringArb = Arb.string(1..100).filter { it.isNotBlank() }
        private val filenameArb = Arb.string(1..30).filter { it.isNotBlank() && !it.contains('/') }

        @Test
        fun `summary is preserved exactly`() = runBlocking {
            forAll(nonBlankStringArb) { summary ->
                val proposal = CodeProposal(summary, listOf(sampleAutomation))
                proposal.summary == summary
            }
        }

        @Test
        fun `files list size is preserved`() = runBlocking {
            forAll(Arb.int(1..5)) { count ->
                val files = (1..count).map { i ->
                    FileProposal.automation("code$i", "file$i.star")
                }
                val proposal = CodeProposal("summary", files)
                proposal.files.size == count
            }
        }

        @Test
        fun `getLibraries plus getAutomations equals all files`() = runBlocking {
            // Test with various combinations
            val combinations = listOf(
                listOf(sampleAutomation),
                listOf(sampleLibrary),
                listOf(sampleLibrary, sampleAutomation),
                listOf(sampleAutomation, sampleLibrary),
                listOf(
                    FileProposal.library("c1", "lib/a.lib.star"),
                    FileProposal.automation("c2", "b.star"),
                    FileProposal.library("c3", "lib/c.lib.star")
                )
            )
            
            combinations.forEach { files ->
                val proposal = CodeProposal("test", files)
                val reconstructed = proposal.getLibraries() + proposal.getAutomations()
                assertEquals(files.size, reconstructed.size)
            }
            true
        }
    }

    @Nested
    inner class Equality {
        @Test
        fun `proposals with same properties should be equal`() {
            val p1 = CodeProposal("summary", listOf(sampleAutomation))
            val p2 = CodeProposal("summary", listOf(sampleAutomation))
            assertEquals(p1, p2)
        }

        @Test
        fun `proposals with different summaries should not be equal`() {
            val p1 = CodeProposal("summary1", listOf(sampleAutomation))
            val p2 = CodeProposal("summary2", listOf(sampleAutomation))
            assertNotEquals(p1, p2)
        }

        @Test
        fun `proposals with different files should not be equal`() {
            val p1 = CodeProposal("summary", listOf(sampleAutomation))
            val p2 = CodeProposal("summary", listOf(sampleLibrary))
            assertNotEquals(p1, p2)
        }
    }
}
