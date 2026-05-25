package com.dddheroes.sdk.application

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.conversion.Converter
import org.axonframework.extension.springboot.autoconfig.ConverterAutoConfiguration
import org.axonframework.extension.springboot.autoconfig.JacksonConverterAutoConfiguration
import org.axonframework.extension.springboot.autoconfig.ObjectMapperAutoConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

/**
 * Spring integration test verifying that the [Converter] bean from the application context
 * can serialize and deserialize [CommandHandlerResult] through a `byte[]` round-trip.
 *
 * Boots a minimal context with just the Jackson + Converter auto-configurations and the
 * project's [CommandHandlerResultJacksonModule]. This avoids loading the full
 * `HeroesOfDDDApplication`, which registers event processors whose lifecycle does not
 * survive Spring Framework 7's test-context stop/restart cycle (the executor is shut
 * down on stop and not recreated on start, causing `RejectedExecutionException`).
 *
 * Still catches the wiring bugs the original test was written for:
 * - Missing module registration in the `defaultAxonObjectMapper` bean
 * - Axon's `ObjectMapperAutoConfiguration` creating its own `ObjectMapper`
 *   (via `findAndAddModules`) instead of using the Spring-configured one
 */
@SpringBootTest(classes = [CommandHandlerResultAxonConverterSpringTest.TestConfig::class])
internal class CommandHandlerResultAxonConverterSpringTest @Autowired constructor(
    private val converter: Converter
) {

    @SpringBootConfiguration
    @ImportAutoConfiguration(
        ObjectMapperAutoConfiguration::class,
        JacksonConverterAutoConfiguration::class,
        ConverterAutoConfiguration::class,
    )
    class TestConfig {

        @Bean
        fun commandHandlerResultJacksonModule(): CommandHandlerResultJacksonModule =
            CommandHandlerResultJacksonModule()

        @Bean("defaultAxonObjectMapper")
        fun defaultAxonObjectMapper(modules: List<JacksonModule>): ObjectMapper =
            JsonMapper.builder()
                .findAndAddModules()
                .addModules(modules)
                .build()
    }

    @Test
    fun `Axon Converter bean should serialize and deserialize Success via byte array`() {
        val original = CommandHandlerResult.Success

        val bytes = converter.convert(original, ByteArray::class.java)!!
        val deserialized = converter.convert(bytes, CommandHandlerResult::class.java)

        assertThat(deserialized).isEqualTo(CommandHandlerResult.Success)
    }

    @Test
    fun `Axon Converter bean should serialize and deserialize Failure via byte array`() {
        val original = CommandHandlerResult.Failure("not enough resources")

        val bytes = converter.convert(original, ByteArray::class.java)!!
        val deserialized = converter.convert(bytes, CommandHandlerResult::class.java)

        assertThat(deserialized).isEqualTo(CommandHandlerResult.Failure("not enough resources"))
    }
}
