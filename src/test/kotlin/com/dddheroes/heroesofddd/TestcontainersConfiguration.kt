package com.dddheroes.heroesofddd

import org.axonframework.test.server.AxonServerContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Profile("testcontainers & axonserver")
    @Bean
    @ServiceConnection
    fun axonServerContainer(): AxonServerContainer {
        return AxonServerContainer("axoniq/axonserver:latest")
            .withDevMode(true)
            .withDcbContext(true)
    }

//    @Profile("testcontainers")
//    @Bean
//    @ServiceConnection
//    fun postgresContainer(): PostgreSQLContainer {
//        return PostgreSQLContainer("postgres:latest")
//    }

}
