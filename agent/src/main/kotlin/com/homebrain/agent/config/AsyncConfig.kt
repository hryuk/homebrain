package com.homebrain.agent.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Configuration for async operations.
 * 
 * Enables async execution for embedding updates, which are fire-and-forget
 * operations that should not block API responses.
 */
@Configuration
@EnableAsync
class AsyncConfig {
    
    @Bean("embeddingExecutor")
    fun embeddingExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("embedding-async-")
        executor.initialize()
        return executor
    }
}
