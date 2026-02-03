package com.dddheroes.heroesofddd.resourcespool.events

import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourcesPoolId

data class ResourcesWithdrawn(
    override val resourcesPoolId: ResourcesPoolId,
    val resources: Map<String, Int> // todo: change String to ResourceType?
) : ResourcesPoolEvent