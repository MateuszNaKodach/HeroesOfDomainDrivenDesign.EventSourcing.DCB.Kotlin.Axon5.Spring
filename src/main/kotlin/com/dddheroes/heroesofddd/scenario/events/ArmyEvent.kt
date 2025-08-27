package com.dddheroes.heroesofddd.scenario.events

import com.dddheroes.heroesofddd.EventTags
import org.axonframework.eventsourcing.annotations.EventTag

sealed interface ArmyEvent : ScenarioEvent {
    @get:EventTag(EventTags.ARMY_ID)
    val armyId: String
}

sealed interface ArmyExpansionEvent : ArmyEvent {
    val creatureId: String
    val quantity: Int
}

sealed interface ArmyReductionEvent : ArmyEvent {
    val creatureId: String
    val quantity: Int
}