package com.dddheroes.heroesofddd.resourcespool.events

data class ResourcesWithdrawn(
    override val resourcesPoolId: String,
    val resources: Map<String, Int>
) : ResourcesPoolEvent