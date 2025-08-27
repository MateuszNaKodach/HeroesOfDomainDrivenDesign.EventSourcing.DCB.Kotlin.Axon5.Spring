package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import org.axonframework.eventsourcing.annotations.EventTag

sealed interface ArmyEvent : HeroesEvent {
    @get:EventTag(EventTags.ARMY_ID)
    val armyId: String
}