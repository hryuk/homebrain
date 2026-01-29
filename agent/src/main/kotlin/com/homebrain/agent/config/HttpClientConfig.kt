package com.homebrain.agent.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.ReactorClientHttpRequestFactory
import reactor.netty.http.client.HttpClient
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Configuration for HTTP client timeouts.
 * 
 * This configuration addresses the timeout issue when calling the Anthropic API.
 * LLM API calls can take 30-120 seconds for complex requests with tool calling,
 * so we need to configure appropriate timeouts.
 * 
 * The default timeouts in reactor-netty are often too short (10-30 seconds),
 * causing ReadTimeoutException when the LLM takes longer to respond.
 * 
 * Embabel's AnthropicModelsConfig looks for a bean named "aiModelHttpRequestFactory"
 * to configure the HTTP client used for Anthropic API calls.
 */
@Configuration
class HttpClientConfig {

    companion object {
        // Connection timeout: how long to wait to establish a connection
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(30)
        
        // Read timeout: how long to wait for the response
        // LLM responses can take 60-180 seconds for complex tool-calling requests
        private val READ_TIMEOUT: Duration = Duration.ofMinutes(5)
    }

    /**
     * Provides a ClientHttpRequestFactory bean with extended timeouts for AI model calls.
     * 
     * This bean is specifically named "aiModelHttpRequestFactory" because Embabel's
     * AnthropicModelsConfig looks for this bean name when configuring the RestClient
     * used for Anthropic API calls.
     * 
     * @see com.embabel.agent.config.models.anthropic.AnthropicModelsConfig
     */
    @Bean("aiModelHttpRequestFactory")
    fun aiModelHttpRequestFactory(): ClientHttpRequestFactory {
        logger.info { "Creating aiModelHttpRequestFactory with extended timeouts: connect=$CONNECT_TIMEOUT, read=$READ_TIMEOUT" }
        
        val httpClient = HttpClient.create()
            .responseTimeout(READ_TIMEOUT)
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT.toMillis().toInt())
        
        return ReactorClientHttpRequestFactory(httpClient)
    }
}
