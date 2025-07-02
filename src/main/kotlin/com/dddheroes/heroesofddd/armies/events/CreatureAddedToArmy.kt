package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesPoolEvent
import org.axonframework.eventsourcing.annotations.EventTag

data class CreatureAddedToArmy(
    @EventTag(EventTags.ARMY_ID)
    override val armyId: String,
    val creatureId: String,
    val quantity: Int
) : ArmyEvent