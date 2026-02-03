package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.eventsourcing.annotation.EventTag

data class CreatureRecruited(
    override val dwellingId: DwellingId,
    val creatureId: String,
    @EventTag(EventTags.ARMY_ID)
    val toArmy: String,
    val quantity: Int,
    val totalCost: Map<ResourceType, Int>
) : DwellingEvent
