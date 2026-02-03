package com.dddheroes.heroesofddd.resourcespool.events

import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import org.testcontainers.shaded.com.google.common.io.Resources

data class ResourcesDeposited(
    override val resourcesPoolId: ResourcesPoolId,
    val resources: Resources
) : ResourcesPoolEvent