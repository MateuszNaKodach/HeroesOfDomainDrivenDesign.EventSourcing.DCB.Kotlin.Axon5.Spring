package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.valueobjects.DwellingId
import org.axonframework.eventsourcing.annotation.EventTag

sealed interface DwellingEvent : HeroesEvent {
    @get:EventTag(EventTags.DWELLING_ID)
    val dwellingId: DwellingId
}