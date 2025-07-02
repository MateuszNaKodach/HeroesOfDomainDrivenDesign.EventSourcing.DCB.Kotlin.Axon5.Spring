package com.dddheroes.heroesofddd.resourcespool.events

import com.dddheroes.heroesofddd.EventTags
import org.axonframework.eventsourcing.annotations.EventTag

data class ResourcesWithdrawn(
    @EventTag(EventTags.RESOURCES_POOL_ID)
    override val resourcesPoolId: String,
    val resources: Map<String, Int>
) : ResourcesPoolEvent