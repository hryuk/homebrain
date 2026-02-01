package com.homebrain.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan("com.homebrain.agent.config")
class AgentApplication

fun main(args: Array<String>) {
    runApplication<AgentApplication>(*args)
}
