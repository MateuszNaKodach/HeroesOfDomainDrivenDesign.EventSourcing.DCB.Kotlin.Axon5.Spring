package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.shared.domain.valueobjects.ArmyId

data class CreatureAddedToArmy(
    override val armyId: ArmyId,
    val creatureId: String,
    val quantity: Int
) : ArmyEvent