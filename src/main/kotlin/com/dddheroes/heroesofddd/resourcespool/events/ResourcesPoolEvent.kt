package com.dddheroes.heroesofddd.resourcespool.events

import com.dddheroes.heroesofddd.shared.domain.HeroesEvent

sealed interface ResourcesPoolEvent : HeroesEvent {
    val resourcesPoolId: String
}