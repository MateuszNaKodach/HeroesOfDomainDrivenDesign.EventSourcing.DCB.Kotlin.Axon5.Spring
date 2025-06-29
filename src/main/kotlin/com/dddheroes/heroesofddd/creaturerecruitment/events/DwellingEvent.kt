package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.shared.domain.HeroesEvent

sealed interface DwellingEvent : HeroesEvent {
    val dwellingId: String
}