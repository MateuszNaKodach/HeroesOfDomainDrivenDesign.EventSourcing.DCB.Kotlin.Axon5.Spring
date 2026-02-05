package com.dddheroes.heroesofddd.resourcespool.events

import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources

data class ResourcesWithdrawn(
    override val resourcesPoolId: ResourcesPoolId,
    val resources: Resources
) : ResourcesPoolEvent