package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import org.axonframework.messaging.eventhandling.annotation.Event

@Event(namespace = "Armies", name = "CreatureAddedToArmy", version = "1.0.0")
data class CreatureAddedToArmy(
    override val armyId: ArmyId,
    val creatureId: CreatureId,
    val quantity: Quantity
) : ArmyEvent