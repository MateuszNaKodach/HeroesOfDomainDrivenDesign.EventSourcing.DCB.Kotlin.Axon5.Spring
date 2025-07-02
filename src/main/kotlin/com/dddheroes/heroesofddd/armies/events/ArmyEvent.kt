package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.shared.domain.HeroesEvent

sealed interface ArmyEvent : HeroesEvent {
    val armyId: String
}