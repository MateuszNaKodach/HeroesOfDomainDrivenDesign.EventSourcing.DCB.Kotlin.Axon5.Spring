package com.dddheroes.heroesofddd.resourcespool.events

data class ResourcesDeposited(
    override val resourcesPoolId: String,
    val resources: Map<String, Int>
) : ResourcesPoolEvent