package com.dddheroes.heroesofddd.armies.events

data class CreatureRemovedFromArmy(
    override val armyId: String,
    val creatureId: String,
    val quantity: Int
) : ArmyEvent