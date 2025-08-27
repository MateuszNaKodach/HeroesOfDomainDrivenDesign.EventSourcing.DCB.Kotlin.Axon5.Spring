package com.dddheroes.heroesofddd.scenario.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.eventsourcing.annotations.EventTag

data class DwellingBuilt(
    @get:EventTag(EventTags.DWELLING_ID)
    override val dwellingId: String,
    val creatureId: String,
    val costPerTroop: Map<ResourceType, Int>
) : DwellingEvent
