package com.dddheroes.heroesofddd.resourcespool.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourcesPoolId
import org.axonframework.eventsourcing.annotation.EventTag

sealed interface ResourcesPoolEvent : HeroesEvent {
    @get:EventTag(EventTags.RESOURCES_POOL_ID)
    val resourcesPoolId: ResourcesPoolId
}