package com.dddheroes.heroesofddd.scenario.events

data class CreatureAddedToArmy(
    override val armyId: String,
    val creatureId: String,
    val quantity: Int
) : ArmyEvent