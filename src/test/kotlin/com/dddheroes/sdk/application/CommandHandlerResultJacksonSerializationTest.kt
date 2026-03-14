package com.dddheroes.sdk.application

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.conversion.jackson.JacksonConverter
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

/**
 * Tests that [CommandHandlerResult] can be serialized and deserialized
 * through the same code path Axon Server uses for command results.
 *
 * When a command result travels through Axon Server (gRPC), it is:
 * 1. Serialized to `byte[]` by `JacksonConverter` on the handler side
 * 2. Deserialized back from `byte[]` by `JacksonConverter` on the gateway side
 *
 * Without [CommandHandlerResultJacksonModule], Jackson cannot construct [CommandHandlerResult]
 * because it's a sealed class with no default constructor or type information.
 *
 * Note: `@AxonSpringBootTest` slice tests do NOT catch this issue because
 * command handlers run in the same JVM. [GenericMessage.payloadAs] short-circuits at:
 * ```
 * if (type instanceof Class clazz && clazz.isAssignableFrom(payloadType())) {
 *     return (T) payload();  // no Converter invoked!
 * }
 * ```
 * The payload is already [CommandHandlerResult], so the [Converter] is never called.
 * The bug only manifests when Axon Server serializes the result to `byte[]` over gRPC.
 */
internal class CommandHandlerResultJacksonSerializationTest {

    private val objectMapper: ObjectMapper = JsonMapper.builder()
        .addModule(CommandHandlerResultJacksonModule())
        .build()

    @Nested
    inner class JacksonObjectMapper {

        @Test
        fun `should serialize and deserialize Success`() {
            val original = CommandHandlerResult.Success

            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue(json, CommandHandlerResult::class.java)

            assertThat(deserialized).isEqualTo(CommandHandlerResult.Success)
        }

        @Test
        fun `should serialize Success with type discriminator`() {
            val json = objectMapper.writeValueAsString(CommandHandlerResult.Success)

            assertThat(json).contains("\"type\"")
            assertThat(json).contains("\"Success\"")
        }

        @Test
        fun `should serialize and deserialize Failure`() {
            val original = CommandHandlerResult.Failure("not enough resources")

            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue(json, CommandHandlerResult::class.java)

            assertThat(deserialized).isEqualTo(CommandHandlerResult.Failure("not enough resources"))
        }

        @Test
        fun `should preserve failure message through round-trip`() {
            val message = "Dwelling already built"
            val original = CommandHandlerResult.Failure(message)

            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue(json, CommandHandlerResult::class.java)

            assertThat(deserialized).isInstanceOf(CommandHandlerResult.Failure::class.java)
            assertThat((deserialized as CommandHandlerResult.Failure).message).isEqualTo(message)
        }
    }

    /**
     * Tests with Axon's [JacksonConverter] — the exact class from the error stack trace.
     * Simulates the Axon Server round-trip: object → byte[] → object.
     */
    @Nested
    inner class AxonJacksonConverter {

        private val converter = JacksonConverter(objectMapper)

        @Test
        fun `should convert Success to bytes and back`() {
            val original = CommandHandlerResult.Success

            val bytes = converter.convert(original, ByteArray::class.java)!!
            val deserialized = converter.convert(bytes, CommandHandlerResult::class.java)

            assertThat(deserialized).isEqualTo(CommandHandlerResult.Success)
        }

        @Test
        fun `should convert Failure to bytes and back`() {
            val original = CommandHandlerResult.Failure("not enough resources")

            val bytes = converter.convert(original, ByteArray::class.java)!!
            val deserialized = converter.convert(bytes, CommandHandlerResult::class.java)

            assertThat(deserialized).isEqualTo(CommandHandlerResult.Failure("not enough resources"))
        }
    }
}
