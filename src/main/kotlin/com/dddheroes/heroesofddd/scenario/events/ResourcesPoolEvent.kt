package com.dddheroes.heroesofddd.scenario.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.eventsourcing.annotations.EventTag

sealed interface ResourcesPoolEvent : ScenarioEvent {
    @get:EventTag(EventTags.RESOURCES_POOL_ID)
    val resourcesPoolId: String
}

sealed interface ResourcesIncomeEvent : ResourcesPoolEvent {
    val gained: Map<ResourceType, Int>
}

sealed interface ResourcesExpenseEvent : ResourcesPoolEvent {
    val spent: Map<ResourceType, Int>
}