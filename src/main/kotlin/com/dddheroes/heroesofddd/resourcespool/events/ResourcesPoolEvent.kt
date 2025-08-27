package com.dddheroes.heroesofddd.resourcespool.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import org.axonframework.eventsourcing.annotations.EventTag

sealed interface ResourcesPoolEvent : HeroesEvent {
    @get:EventTag(EventTags.RESOURCES_POOL_ID)
    val resourcesPoolId: String
}