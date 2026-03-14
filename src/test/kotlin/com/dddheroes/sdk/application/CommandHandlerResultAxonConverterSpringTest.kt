package com.dddheroes.sdk.application

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.HeroesOfDDDApplication
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.conversion.Converter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Spring integration test verifying that the [Converter] bean from the application context
 * can serialize and deserialize [CommandHandlerResult] through a `byte[]` round-trip.
 *
 * This tests the full Spring wiring: `defaultAxonObjectMapper` bean → `ConverterAutoConfiguration`
 * → `JacksonConverter` with all Spring `@Component` [JacksonModule] beans registered
 * (including [CommandHandlerResultJacksonModule]).
 *
 * Unlike unit tests that create their own `ObjectMapper`, this catches wiring issues like:
 * - Missing module registration in the `defaultAxonObjectMapper` bean
 * - Axon's `ObjectMapperAutoConfiguration` creating its own `ObjectMapper` (via `findAndAddModules`)
 *   instead of using the Spring-configured one
 *
 * Unlike `@AxonSpringBootTest` slice tests where [GenericMessage.payloadAs] short-circuits
 * (payload is already the correct Java type), this test forces a full serialize/deserialize
 * round-trip through the [Converter], catching missing Jackson modules.
 */
@HeroesAxonSpringBootTest(classes = [HeroesOfDDDApplication::class])
internal class CommandHandlerResultAxonConverterSpringTest @Autowired constructor(
    private val converter: Converter
) {

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
