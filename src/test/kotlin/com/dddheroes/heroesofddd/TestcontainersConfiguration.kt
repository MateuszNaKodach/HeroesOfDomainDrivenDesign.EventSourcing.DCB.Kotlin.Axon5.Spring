package com.dddheroes.heroesofddd

import io.axoniq.framework.testcontainer.AxonServerContainer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    companion object {
        private val axonServer: AxonServerContainer = AxonServerContainer("axoniq/axonserver:2026.0.0")
            .withDevMode(true)
            .withDcbContext(true)
            .withReuse(true)

        private val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:18")
            .withCommand("postgres", "-c", "max_connections=300")
            .withReuse(true)
    }

    @Profile("testcontainers")
    @ConditionalOnProperty(name = ["axon.axonserver.enabled"], havingValue = "true")
    @Bean
    @ServiceConnection
    fun axonServerContainer(): AxonServerContainer = axonServer

    @Profile("testcontainers")
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = postgres

}
