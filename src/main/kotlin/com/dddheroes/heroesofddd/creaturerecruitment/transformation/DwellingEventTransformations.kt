package com.dddheroes.heroesofddd.creaturerecruitment.transformation

import io.axoniq.framework.messaging.transformation.events.EventTransformation
import io.axoniq.framework.messaging.transformation.events.EventTransformerChain
import org.axonframework.messaging.core.MessageType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/**
 * Event transformations for the CreatureRecruitment dwelling event.
 *
 * History of the dwelling-built event identity:
 *  - `CreatureRecruitment.DwellingBuilt` @1.0.0       (original)
 *  - `CreatureRecruitment.DwellingConstructed` @1.0.0 (renamed, same payload)
 *  - `CreatureRecruitment.DwellingConstructed` @2.0.0 (added `level`)
 *
 * On read, events stored under an older identity are lifted to the current one, one step per
 * version, so handlers and projections only ever see `DwellingConstructed` @2.0.0.
 */
@Configuration
class DwellingEventTransformationsConfig {

    // Toggle the upcaster on/off so the app can be run with and without it. When disabled, no
    // EventTransformerChain bean exists, so the TransformingEventStore decorator is a no-op
    // pass-through and legacy DwellingBuilt@1.0.0 events are read in their stored shape (unhandled).
    @Bean
    @ConditionalOnProperty(
        prefix = "transformations.creaturerecruitment",
        name = ["dwelling.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun dwellingEventTransformerChain(): EventTransformerChain =
        EventTransformerChain.builder()
            .register(renameDwellingBuiltToDwellingConstructed())
            .register(dwellingConstructedV1ToV2())
            .build()

    /** Pure rename: the qualified name changed, the payload is carried over unchanged. */
    private fun renameDwellingBuiltToDwellingConstructed(): EventTransformation =
        EventTransformation.rename(
            MessageType("CreatureRecruitment.DwellingBuilt", "1.0.0"),
            MessageType("CreatureRecruitment.DwellingConstructed", "1.0.0"),
        )

    /** v2 added `level`; events that predate the field are backfilled with 0 ("unknown"). */
    private fun dwellingConstructedV1ToV2(): EventTransformation =
        EventTransformation
            .from(MessageType("CreatureRecruitment.DwellingConstructed", "1.0.0"))
            .to(MessageType("CreatureRecruitment.DwellingConstructed", "2.0.0"))
            .transform(JsonNode::class.java) { v1 -> addLevel(v1) }

    private fun addLevel(v1: JsonNode): JsonNode =
        (v1.deepCopy() as ObjectNode).apply { put("level", 0) }
}
