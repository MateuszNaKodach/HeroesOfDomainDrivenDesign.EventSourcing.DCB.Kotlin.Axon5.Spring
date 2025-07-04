package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.eventsourcing.annotations.EventTag

data class CreatureRecruited(
    @EventTag(EventTags.DWELLING_ID)
    override val dwellingId: String,
    val creatureId: String,
    @EventTag(EventTags.ARMY_ID)
    val toArmy: String,
    val quantity: Int,
    val totalCost: Map<ResourceType, Int>
) : DwellingEvent
