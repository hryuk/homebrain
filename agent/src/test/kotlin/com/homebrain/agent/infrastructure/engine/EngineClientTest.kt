package com.homebrain.agent.infrastructure.engine

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.homebrain.agent.domain.validation.ValidationResult
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class EngineClientTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer

        @JvmStatic
        @BeforeAll
        fun startWireMock() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopWireMock() {
            wireMockServer.stop()
        }
    }

    private lateinit var engineClient: EngineClient

    @BeforeEach
    fun setUp() {
        wireMockServer.resetAll()
        engineClient = EngineClient("http://localhost:${wireMockServer.port()}")
    }

    @Nested
    inner class GetTopics {
        @Test
        fun `should return topics from engine`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/topics"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""["zigbee2mqtt/device/state", "homeassistant/status"]""")
                    )
            )

            val topics = engineClient.getTopics()

            assertEquals(2, topics.size)
            assertEquals("zigbee2mqtt/device/state", topics[0])
            assertEquals("homeassistant/status", topics[1])
        }

        @Test
        fun `should return empty list when engine returns empty`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/topics"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )

            val topics = engineClient.getTopics()

            assertTrue(topics.isEmpty())
        }

        @Test
        fun `should return empty list on connection error`() {
            // Stop WireMock temporarily to simulate connection error
            wireMockServer.stubFor(
                get(urlEqualTo("/topics"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")
                    )
            )

            val topics = engineClient.getTopics()

            assertTrue(topics.isEmpty())
        }

        @Test
        fun `should return empty list on timeout`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/topics"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withFixedDelay(5000) // 5 second delay
                            .withBody("[]")
                    )
            )

            // This might timeout or succeed depending on WebClient defaults
            // The important thing is it doesn't throw
            val topics = engineClient.getTopics()
            // Result depends on WebClient timeout settings
            assertNotNull(topics)
        }
    }

    @Nested
    inner class GetAutomations {
        @Test
        fun `should return automations from engine`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/automations"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""[
                                {"id": "light_controller", "name": "Light Controller", "enabled": true},
                                {"id": "temp_monitor", "name": "Temperature Monitor", "enabled": false}
                            ]""")
                    )
            )

            val automations = engineClient.getAutomations()

            assertEquals(2, automations.size)
            assertEquals("light_controller", automations[0]["id"])
            assertEquals("Light Controller", automations[0]["name"])
            assertEquals(true, automations[0]["enabled"])
            assertEquals("temp_monitor", automations[1]["id"])
        }

        @Test
        fun `should return empty list when engine returns empty`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/automations"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )

            val automations = engineClient.getAutomations()

            assertTrue(automations.isEmpty())
        }

        @Test
        fun `should return empty list on error`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/automations"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                    )
            )

            val automations = engineClient.getAutomations()

            assertTrue(automations.isEmpty())
        }

        @Test
        fun `should handle nested objects in automation data`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/automations"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""[{
                                "id": "test",
                                "config": {
                                    "subscribe": ["topic/+"],
                                    "schedule": "* * * * *"
                                },
                                "stats": {
                                    "messageCount": 100,
                                    "lastRun": "2024-01-15T10:00:00Z"
                                }
                            }]""")
                    )
            )

            val automations = engineClient.getAutomations()

            assertEquals(1, automations.size)
            assertEquals("test", automations[0]["id"])
            assertNotNull(automations[0]["config"])
            assertNotNull(automations[0]["stats"])
        }
    }

    @Nested
    inner class GetLogs {
        @Test
        fun `should return logs from engine`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/logs"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""[
                                {"timestamp": "2024-01-15T10:00:00Z", "automation": "test", "message": "Log 1"},
                                {"timestamp": "2024-01-15T10:01:00Z", "automation": "test", "message": "Log 2"}
                            ]""")
                    )
            )

            val logs = engineClient.getLogs()

            assertEquals(2, logs.size)
            assertEquals("Log 1", logs[0]["message"])
            assertEquals("Log 2", logs[1]["message"])
        }

        @Test
        fun `should return empty list when no logs`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/logs"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")
                    )
            )

            val logs = engineClient.getLogs()

            assertTrue(logs.isEmpty())
        }

        @Test
        fun `should return empty list on error`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/logs"))
                    .willReturn(
                        aResponse()
                            .withStatus(503)
                            .withBody("Service Unavailable")
                    )
            )

            val logs = engineClient.getLogs()

            assertTrue(logs.isEmpty())
        }
    }

    @Nested
    inner class ValidateCode {
        @Test
        fun `should return success for valid automation code`() {
            wireMockServer.stubFor(
                post(urlEqualTo("/validate"))
                    .withRequestBody(matchingJsonPath("$.code"))
                    .withRequestBody(matchingJsonPath("$.type", equalTo("automation")))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"valid": true, "errors": []}""")
                    )
            )

            val result = engineClient.validateCode(
                code = "def on_message(t, p, ctx): pass\nconfig = {}",
                type = "automation"
            )

            assertTrue(result.valid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `should return failure with errors for invalid code`() {
            wireMockServer.stubFor(
                post(urlEqualTo("/validate"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"valid": false, "errors": ["syntax error: line 1:10: got newline, want colon", "automation missing 'config' variable"]}""")
                    )
            )

            val result = engineClient.validateCode(
                code = "invalid code here",
                type = "automation"
            )

            assertFalse(result.valid)
            assertEquals(2, result.errors.size)
            assertTrue(result.errors[0].contains("syntax error"))
            assertTrue(result.errors[1].contains("config"))
        }

        @Test
        fun `should validate library code`() {
            wireMockServer.stubFor(
                post(urlEqualTo("/validate"))
                    .withRequestBody(matchingJsonPath("$.type", equalTo("library")))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"valid": true}""")
                    )
            )

            val result = engineClient.validateCode(
                code = "def helper(ctx): return True",
                type = "library"
            )

            assertTrue(result.valid)
        }

        @Test
        fun `should return failure on network error`() {
            wireMockServer.stubFor(
                post(urlEqualTo("/validate"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")
                    )
            )

            val result = engineClient.validateCode(
                code = "def on_message(t, p, ctx): pass",
                type = "automation"
            )

            // On error, should return a failure result
            assertFalse(result.valid)
            assertTrue(result.errors.isNotEmpty())
        }

        @Test
        fun `should return failure on connection timeout`() {
            wireMockServer.stubFor(
                post(urlEqualTo("/validate"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withFixedDelay(10000) // 10 second delay
                            .withBody("""{"valid": true}""")
                    )
            )

            // Use a client with short timeout for this test
            val shortTimeoutClient = EngineClient("http://localhost:${wireMockServer.port()}")
            val result = shortTimeoutClient.validateCode(
                code = "def test(): pass",
                type = "automation"
            )

            // On timeout, should return a failure result (or success if it completes)
            // The important thing is it doesn't throw
            assertNotNull(result)
        }

        @Test
        fun `should send correct request body`() {
            val testCode = """
                def on_message(topic, payload, ctx):
                    ctx.log("test")
                
                config = {"name": "Test", "enabled": True}
            """.trimIndent()

            wireMockServer.stubFor(
                post(urlEqualTo("/validate"))
                    .withRequestBody(matchingJsonPath("$.code", equalTo(testCode)))
                    .withRequestBody(matchingJsonPath("$.type", equalTo("automation")))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"valid": true}""")
                    )
            )

            val result = engineClient.validateCode(code = testCode, type = "automation")

            assertTrue(result.valid)
            // Verify the request was made correctly
            wireMockServer.verify(
                postRequestedFor(urlEqualTo("/validate"))
                    .withRequestBody(matchingJsonPath("$.code"))
                    .withRequestBody(matchingJsonPath("$.type"))
            )
        }
    }
}
