package com.dddheroes.heroesofddd.scenario.events

class WanderingCreaturesJoined(
    override val armyId: String,
    override val creatureId: String,
    override val quantity: Int
) : ArmyExpansionEvent