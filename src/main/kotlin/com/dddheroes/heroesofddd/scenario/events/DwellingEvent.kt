package com.dddheroes.heroesofddd.scenario.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import org.axonframework.eventsourcing.annotations.EventTag

sealed interface DwellingEvent : HeroesEvent {
    @get:EventTag(EventTags.DWELLING_ID)
    val dwellingId: String
}