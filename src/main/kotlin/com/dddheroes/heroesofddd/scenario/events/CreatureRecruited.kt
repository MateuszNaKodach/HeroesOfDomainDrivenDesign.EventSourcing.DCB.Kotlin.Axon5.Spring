package com.dddheroes.heroesofddd.scenario.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.eventsourcing.annotations.EventTag

data class CreatureRecruited(
    override val dwellingId: String,
    override val creatureId: String,
    @EventTag(EventTags.ARMY_ID)
    val toArmy: String,
    override val quantity: Int,
    val totalCost: Map<ResourceType, Int>
) : DwellingEvent, ArmyExpansionEvent {
    override val armyId: String
        get() = toArmy
}
