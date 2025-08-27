package com.dddheroes.heroesofddd.scenario.events

data class ResourcesWithdrawn(
    override val resourcesPoolId: String,
    val resources: Map<String, Int>
) : ResourcesPoolEvent