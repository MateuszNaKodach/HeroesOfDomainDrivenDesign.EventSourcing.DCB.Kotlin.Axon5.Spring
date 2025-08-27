package com.dddheroes.heroesofddd.scenario.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.eventsourcing.annotations.EventTag

data class CreatureRecruited(
    override val dwellingId: String,
    val creatureId: String,
    @EventTag(EventTags.ARMY_ID)
    val toArmy: String,
    val quantity: Int,
    val totalCost: Map<ResourceType, Int>
) : DwellingEvent
