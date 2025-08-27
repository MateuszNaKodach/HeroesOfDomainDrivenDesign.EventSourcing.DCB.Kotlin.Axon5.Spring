package com.dddheroes.heroesofddd.scenario.events

import com.dddheroes.heroesofddd.EventTags
import org.axonframework.eventsourcing.annotations.EventTag

sealed interface DwellingEvent : ScenarioEvent {
    @get:EventTag(EventTags.DWELLING_ID)
    val dwellingId: String
}