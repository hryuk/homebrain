package com.homebrain.agent.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test that calls the REAL Anthropic API to reproduce the timeout bug.
 * 
 * This test requires ANTHROPIC_API_KEY environment variable to be set.
 * 
 * The test sends a complex request that will take 30+ seconds to process,
 * which should trigger the ReadTimeoutException if the timeout is not properly configured.
 * 
 * TEST BEHAVIOR:
 * - FAILS with ReadTimeoutException/500 error if timeout bug exists
 * - PASSES if timeout is properly configured (2+ minutes)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicTimeoutTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.engine.url") { "http://localhost:9999" }
            registry.add("app.automations.path") { "/tmp/test-automations" }
        }
    }

    /**
     * This test reproduces the timeout bug by sending a complex request that
     * requires the LLM to:
     * 1. Think carefully about the problem
     * 2. Generate detailed Starlark code
     * 3. Explain the solution
     * 
     * This typically takes 20-60+ seconds and will trigger the timeout bug
     * if the HTTP client timeout is set too low (default ~10-30s).
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    fun `should handle long-running LLM request without timeout`() {
        java.io.File("/tmp/test-automations").mkdirs()
        
        // Complex prompt that requires significant processing time
        // This is similar to what a user would ask when creating an automation
        val request = mapOf(
            "message" to """
                I want to create a sophisticated automation for my smart home. Here's what I need:
                
                When motion is detected on zigbee2mqtt/living_room/motion_sensor, I want the system to:
                1. Check if it's between sunset and sunrise (nighttime)
                2. If nighttime, turn on zigbee2mqtt/living_room/light/set to 50% brightness
                3. If daytime, don't turn on the light but log that motion was detected
                4. After 5 minutes of no motion, turn off the light
                5. Keep track of how many times motion was detected today using persistent state
                6. If motion is detected more than 20 times in an hour, send an alert to zigbee2mqtt/alerts/set
                
                Please create the complete Starlark automation code with all the logic, 
                state management, and proper error handling. Include detailed comments 
                explaining each part of the code.
            """.trimIndent()
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val entity = HttpEntity(request, headers)

        println("=".repeat(80))
        println("TIMEOUT BUG REPRODUCTION TEST")
        println("=".repeat(80))
        println("Sending complex automation request to Anthropic API...")
        println("This request should take 20-60+ seconds to complete.")
        println("If timeout bug exists, this will fail with ReadTimeoutException.")
        println("=".repeat(80))
        
        val startTime = System.currentTimeMillis()

        val response = restTemplate.postForEntity(
            "/api/chat",
            entity,
            Map::class.java
        )

        val elapsedTime = System.currentTimeMillis() - startTime
        println("=".repeat(80))
        println("Response received in ${elapsedTime}ms (${elapsedTime / 1000}s)")
        println("Status: ${response.statusCode}")
        println("=".repeat(80))
        
        if (!response.statusCode.is2xxSuccessful) {
            println("FAILURE BODY: ${response.body}")
        }

        assertTrue(
            response.statusCode.is2xxSuccessful,
            "Request failed with status ${response.statusCode}. " +
            "If this is a 500 error with ReadTimeoutException, the timeout bug is reproduced. " +
            "Body: ${response.body}"
        )
        assertNotNull(response.body, "Response body should not be null")
        
        println("SUCCESS: Request completed in ${elapsedTime / 1000} seconds without timeout")
    }
}
