package com.dddheroes.heroesofddd.armies.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ArmyId
import org.axonframework.eventsourcing.annotation.EventTag

sealed interface ArmyEvent : HeroesEvent {
    @get:EventTag(EventTags.ARMY_ID)
    val armyId: ArmyId
}