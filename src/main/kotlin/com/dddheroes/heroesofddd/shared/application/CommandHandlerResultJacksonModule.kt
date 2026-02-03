package com.dddheroes.heroesofddd.shared.application

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.stereotype.Component

@Component
class CommandHandlerResultJacksonModule : SimpleModule() {

    init {
        addSerializer(CommandHandlerResult::class.java, CommandHandlerResultSerializer())
        addDeserializer(CommandHandlerResult::class.java, CommandHandlerResultDeserializer())
    }

    private class CommandHandlerResultSerializer : JsonSerializer<CommandHandlerResult>() {
        override fun serialize(value: CommandHandlerResult, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeStartObject()
            when (value) {
                is CommandHandlerResult.Success -> {
                    gen.writeStringField("type", "Success")
                }

                is CommandHandlerResult.Failure -> {
                    gen.writeStringField("type", "Failure")
                    gen.writeStringField("message", value.message)
                }
            }
            gen.writeEndObject()
        }
    }

    private class CommandHandlerResultDeserializer : JsonDeserializer<CommandHandlerResult>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): CommandHandlerResult {
            val node: JsonNode = parser.codec.readTree(parser)
            val type = node.get("type")?.asText()

            return when (type) {
                "Success" -> CommandHandlerResult.Success
                "Failure" -> {
                    val message = node.get("message")?.asText() ?: "Unknown error"
                    CommandHandlerResult.Failure(message)
                }

                else -> throw IllegalArgumentException("Unknown CommandHandlerResult type: $type")
            }
        }
    }
}
