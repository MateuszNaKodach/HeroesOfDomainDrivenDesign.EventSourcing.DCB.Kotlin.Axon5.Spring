package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.EventTags
import org.axonframework.eventsourcing.annotations.EventTag

data class CreatureRemovedFromArmy(
    @EventTag(EventTags.ARMY_ID)
    override val armyId: String,
    val creatureId: String,
    val quantity: Int
) : ArmyEvent