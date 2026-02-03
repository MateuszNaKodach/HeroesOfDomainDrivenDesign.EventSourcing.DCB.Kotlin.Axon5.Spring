package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.shared.domain.valueobjects.ArmyId

data class CreatureRemovedFromArmy(
    override val armyId: ArmyId,
    val creatureId: String,
    val quantity: Int
) : ArmyEvent