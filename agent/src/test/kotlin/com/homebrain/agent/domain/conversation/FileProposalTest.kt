package com.homebrain.agent.domain.conversation

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FileProposalTest {

    @Nested
    inner class Construction {
        @Test
        fun `should create automation file proposal`() {
            val proposal = FileProposal(
                code = "def on_message(t, p, ctx): pass\nconfig = {}",
                filename = "my_automation.star",
                type = FileProposal.FileType.AUTOMATION
            )
            assertEquals("def on_message(t, p, ctx): pass\nconfig = {}", proposal.code)
            assertEquals("my_automation.star", proposal.filename)
            assertEquals(FileProposal.FileType.AUTOMATION, proposal.type)
        }

        @Test
        fun `should create library file proposal`() {
            val proposal = FileProposal(
                code = "def my_func(ctx): pass",
                filename = "lib/helpers.lib.star",
                type = FileProposal.FileType.LIBRARY
            )
            assertEquals("def my_func(ctx): pass", proposal.code)
            assertEquals("lib/helpers.lib.star", proposal.filename)
            assertEquals(FileProposal.FileType.LIBRARY, proposal.type)
        }

        @Test
        fun `should reject blank code`() {
            val exception = assertThrows<IllegalArgumentException> {
                FileProposal("", "test.star", FileProposal.FileType.AUTOMATION)
            }
            assertEquals("Code cannot be blank", exception.message)
        }

        @Test
        fun `should reject whitespace-only code`() {
            val exception = assertThrows<IllegalArgumentException> {
                FileProposal("   \n\t", "test.star", FileProposal.FileType.AUTOMATION)
            }
            assertEquals("Code cannot be blank", exception.message)
        }

        @Test
        fun `should reject blank filename`() {
            val exception = assertThrows<IllegalArgumentException> {
                FileProposal("def test(): pass", "", FileProposal.FileType.AUTOMATION)
            }
            assertEquals("Filename cannot be blank", exception.message)
        }

        @Test
        fun `should reject whitespace-only filename`() {
            val exception = assertThrows<IllegalArgumentException> {
                FileProposal("def test(): pass", "  ", FileProposal.FileType.AUTOMATION)
            }
            assertEquals("Filename cannot be blank", exception.message)
        }
    }

    @Nested
    inner class FileTypeTests {
        @Test
        fun `toApiString should return lowercase type name`() {
            assertEquals("automation", FileProposal.FileType.AUTOMATION.toApiString())
            assertEquals("library", FileProposal.FileType.LIBRARY.toApiString())
        }

        @ParameterizedTest
        @ValueSource(strings = ["automation", "AUTOMATION", "Automation"])
        fun `fromString should parse automation type case insensitively`(input: String) {
            assertEquals(FileProposal.FileType.AUTOMATION, FileProposal.FileType.fromString(input))
        }

        @ParameterizedTest
        @ValueSource(strings = ["library", "LIBRARY", "Library"])
        fun `fromString should parse library type case insensitively`(input: String) {
            assertEquals(FileProposal.FileType.LIBRARY, FileProposal.FileType.fromString(input))
        }

        @Test
        fun `fromString should throw for unknown type`() {
            val exception = assertThrows<IllegalArgumentException> {
                FileProposal.FileType.fromString("script")
            }
            assertEquals("Unknown file type: script", exception.message)
        }
    }

    @Nested
    inner class HelperMethods {
        @Test
        fun `isLibrary should return true for library type`() {
            val proposal = FileProposal("code", "lib/test.lib.star", FileProposal.FileType.LIBRARY)
            assertTrue(proposal.isLibrary())
            assertFalse(proposal.isAutomation())
        }

        @Test
        fun `isAutomation should return true for automation type`() {
            val proposal = FileProposal("code", "test.star", FileProposal.FileType.AUTOMATION)
            assertTrue(proposal.isAutomation())
            assertFalse(proposal.isLibrary())
        }
    }

    @Nested
    inner class FactoryMethods {
        @Test
        fun `automation factory should create automation type`() {
            val proposal = FileProposal.automation("code", "test.star")
            assertEquals(FileProposal.FileType.AUTOMATION, proposal.type)
        }

        @Test
        fun `library factory should create library type`() {
            val proposal = FileProposal.library("code", "lib/test.lib.star")
            assertEquals(FileProposal.FileType.LIBRARY, proposal.type)
        }
    }

    @Nested
    inner class PropertyBasedTests {
        private val nonBlankStringArb = Arb.string(1..100).filter { it.isNotBlank() }
        private val filenameArb = Arb.string(1..50).filter { it.isNotBlank() && !it.contains('/') }

        @Test
        fun `code is preserved exactly`() = runBlocking {
            forAll(nonBlankStringArb, filenameArb) { code, filename ->
                FileProposal.automation(code, filename).code == code
            }
        }

        @Test
        fun `filename is preserved exactly`() = runBlocking {
            forAll(nonBlankStringArb, filenameArb) { code, filename ->
                FileProposal.automation(code, filename).filename == filename
            }
        }

        @Test
        fun `toApiString and fromString are inverses for valid types`() {
            listOf(FileProposal.FileType.AUTOMATION, FileProposal.FileType.LIBRARY).forEach { type ->
                val apiString = type.toApiString()
                val parsed = FileProposal.FileType.fromString(apiString)
                assertEquals(type, parsed)
            }
        }
    }

    @Nested
    inner class Equality {
        @Test
        fun `proposals with same properties should be equal`() {
            val p1 = FileProposal("code", "file.star", FileProposal.FileType.AUTOMATION)
            val p2 = FileProposal("code", "file.star", FileProposal.FileType.AUTOMATION)
            assertEquals(p1, p2)
        }

        @Test
        fun `proposals with different code should not be equal`() {
            val p1 = FileProposal("code1", "file.star", FileProposal.FileType.AUTOMATION)
            val p2 = FileProposal("code2", "file.star", FileProposal.FileType.AUTOMATION)
            assertNotEquals(p1, p2)
        }

        @Test
        fun `proposals with different types should not be equal`() {
            val p1 = FileProposal("code", "file.star", FileProposal.FileType.AUTOMATION)
            val p2 = FileProposal("code", "file.star", FileProposal.FileType.LIBRARY)
            assertNotEquals(p1, p2)
        }
    }
}
