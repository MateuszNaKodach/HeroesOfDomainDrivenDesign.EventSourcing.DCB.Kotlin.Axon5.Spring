package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.shared.domain.valueobjects.ArmyId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.CreatureId

data class CreatureRemovedFromArmy(
    override val armyId: ArmyId,
    val creatureId: CreatureId,
    val quantity: Int
) : ArmyEvent