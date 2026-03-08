package com.dddheroes.heroesofddd

import org.axonframework.test.server.AxonServerContainer
import org.axonframework.test.server.AxonServerContainerUtils
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.testcontainers.postgresql.PostgreSQLContainer

object TestContainers {
    val axonServer: AxonServerContainer = AxonServerContainer("axoniq/axonserver:2025.2.4")
        .withDevMode(true)
        .withDcbContext(true)

    val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:18")
        .withCommand("postgres", "-c", "max_connections=300")
}

class AxonServerContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(ctx: ConfigurableApplicationContext) {
        TestContainers.axonServer.start()
        AxonServerContainerUtils.purgeEventsFromAxonServer(
            TestContainers.axonServer.host,
            TestContainers.axonServer.httpPort,
            "default",
            AxonServerContainerUtils.DCB_CONTEXT
        )
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Profile("testcontainers")
    @ConditionalOnProperty(name = ["axon.axonserver.enabled"], havingValue = "true")
    @Bean
    @ServiceConnection
    fun axonServerContainer(): AxonServerContainer = TestContainers.axonServer

    @Profile("testcontainers")
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = TestContainers.postgres

}
