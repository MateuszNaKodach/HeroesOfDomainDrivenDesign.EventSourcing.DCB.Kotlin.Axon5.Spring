package com.dddheroes.heroesofddd.creaturerecruitment.transformation

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.conversion.PassThroughConverter
import org.axonframework.messaging.core.MessageStream
import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.core.MessageTypeResolver
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.GenericEventMessage
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Unit test for the dwelling event-transformation chain. Drives the real [EventTransformerChain] over a
 * single stored event and asserts the read-time output, with no event store and no Spring context.
 */
internal class DwellingEventTransformationsTest {

    private val dwellingBuiltV1 = MessageType("CreatureRecruitment.DwellingBuilt", "1.0.0")
    private val dwellingConstructedV2 = MessageType("CreatureRecruitment.DwellingConstructed", "2.0.0")

    private val chain = DwellingEventTransformationsConfig().dwellingEventTransformerChain()

    @Test
    fun `legacy DwellingBuilt 1_0_0 is renamed and upcast to DwellingConstructed 2_0_0 with level 0`() {
        val legacy = event(dwellingBuiltV1, dwellingPayload())

        val output = transform(legacy).single()

        assertThat(output.type()).isEqualTo(dwellingConstructedV2)
        val payload = output.payload() as JsonNode
        assertThat(payload.get("level").asInt()).isZero()
        assertThat(payload.get("dwellingId").asString()).isEqualTo("dwelling-4")
        assertThat(payload.get("creatureId").asString()).isEqualTo("angel")
    }

    @Test
    fun `current DwellingConstructed 2_0_0 passes through unchanged`() {
        val current = event(dwellingConstructedV2, dwellingPayload().apply { put("level", 5) })

        val output = transform(current).single()

        assertThat(output.type()).isEqualTo(dwellingConstructedV2)
        assertThat((output.payload() as JsonNode).get("level").asInt()).isEqualTo(5)
    }

    private fun dwellingPayload(): ObjectNode =
        JsonNodeFactory.instance.objectNode().apply {
            put("dwellingId", "dwelling-4")
            put("creatureId", "angel")
            putObject("costPerTroop")
        }

    private fun event(type: MessageType, payload: Any): EventMessage = GenericEventMessage(type, payload)

    private fun transform(vararg events: EventMessage): List<EventMessage> {
        val collected = mutableListOf<EventMessage>()
        // The transformations declare `JsonNode` as their input type and the stored payloads already are
        // `JsonNode`s, so the chain's fast path never converts; a pass-through converter is enough.
        val converter = DelegatingMessageConverter(PassThroughConverter.INSTANCE)
        chain.transform(MessageStream.fromIterable(events.toList()), null, converter, EmptyResolver)
            .reduce(Unit) { _, entry -> collected.add(entry.message()); Unit }
            .orTimeout(30, TimeUnit.SECONDS)
            .join()
        return collected
    }

    private object EmptyResolver : MessageTypeResolver {
        override fun resolve(payloadType: Class<*>): Optional<MessageType> = Optional.empty()
    }
}
