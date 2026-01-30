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

class MessageTest {

    @Nested
    inner class Construction {
        @Test
        fun `should create message with user role`() {
            val message = Message(Message.Role.USER, "Hello")
            assertEquals(Message.Role.USER, message.role)
            assertEquals("Hello", message.content)
        }

        @Test
        fun `should create message with assistant role`() {
            val message = Message(Message.Role.ASSISTANT, "Hi there!")
            assertEquals(Message.Role.ASSISTANT, message.role)
            assertEquals("Hi there!", message.content)
        }

        @Test
        fun `should reject blank content`() {
            val exception = assertThrows<IllegalArgumentException> {
                Message(Message.Role.USER, "")
            }
            assertEquals("Message content cannot be blank", exception.message)
        }

        @Test
        fun `should reject whitespace-only content`() {
            val exception = assertThrows<IllegalArgumentException> {
                Message(Message.Role.USER, "   \n\t")
            }
            assertEquals("Message content cannot be blank", exception.message)
        }
    }

    @Nested
    inner class FactoryMethods {
        @Test
        fun `user should create user message`() {
            val message = Message.user("What is the weather?")
            assertEquals(Message.Role.USER, message.role)
            assertEquals("What is the weather?", message.content)
        }

        @Test
        fun `assistant should create assistant message`() {
            val message = Message.assistant("The weather is sunny.")
            assertEquals(Message.Role.ASSISTANT, message.role)
            assertEquals("The weather is sunny.", message.content)
        }
    }

    @Nested
    inner class RoleTests {
        @Test
        fun `toApiString should return lowercase role name`() {
            assertEquals("user", Message.Role.USER.toApiString())
            assertEquals("assistant", Message.Role.ASSISTANT.toApiString())
        }

        @ParameterizedTest
        @ValueSource(strings = ["user", "USER", "User", "uSeR"])
        fun `fromString should parse user role case insensitively`(input: String) {
            assertEquals(Message.Role.USER, Message.Role.fromString(input))
        }

        @ParameterizedTest
        @ValueSource(strings = ["assistant", "ASSISTANT", "Assistant", "aSSiStAnT"])
        fun `fromString should parse assistant role case insensitively`(input: String) {
            assertEquals(Message.Role.ASSISTANT, Message.Role.fromString(input))
        }

        @Test
        fun `fromString should throw for unknown role`() {
            val exception = assertThrows<IllegalArgumentException> {
                Message.Role.fromString("system")
            }
            assertEquals("Unknown role: system", exception.message)
        }

        @Test
        fun `fromString should throw for invalid role`() {
            val exception = assertThrows<IllegalArgumentException> {
                Message.Role.fromString("invalid")
            }
            assertEquals("Unknown role: invalid", exception.message)
        }
    }

    @Nested
    inner class PropertyBasedTests {
        private val nonBlankStringArb = Arb.string(1..200).filter { it.isNotBlank() }

        @Test
        fun `user factory always creates USER role`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                Message.user(content).role == Message.Role.USER
            }
        }

        @Test
        fun `assistant factory always creates ASSISTANT role`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                Message.assistant(content).role == Message.Role.ASSISTANT
            }
        }

        @Test
        fun `content is preserved exactly`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                Message.user(content).content == content &&
                Message.assistant(content).content == content
            }
        }

        @Test
        fun `toApiString and fromString are inverses for valid roles`() = runBlocking {
            listOf(Message.Role.USER, Message.Role.ASSISTANT).forEach { role ->
                val apiString = role.toApiString()
                val parsed = Message.Role.fromString(apiString)
                assertEquals(role, parsed)
            }
        }
    }

    @Nested
    inner class Equality {
        @Test
        fun `messages with same role and content should be equal`() {
            val msg1 = Message.user("Hello")
            val msg2 = Message.user("Hello")
            assertEquals(msg1, msg2)
        }

        @Test
        fun `messages with different roles should not be equal`() {
            val msg1 = Message.user("Hello")
            val msg2 = Message.assistant("Hello")
            assertNotEquals(msg1, msg2)
        }

        @Test
        fun `messages with different content should not be equal`() {
            val msg1 = Message.user("Hello")
            val msg2 = Message.user("Hi")
            assertNotEquals(msg1, msg2)
        }
    }
}
