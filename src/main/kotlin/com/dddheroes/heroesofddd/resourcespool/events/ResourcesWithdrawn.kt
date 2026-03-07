package com.dddheroes.heroesofddd.resourcespool.events

import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.axonframework.messaging.eventhandling.annotation.Event

@Event(namespace = "ResourcesPool", name = "ResourcesWithdrawn", version = "1.0.0")
data class ResourcesWithdrawn(
    override val resourcesPoolId: ResourcesPoolId,
    val resources: Resources
) : ResourcesPoolEvent