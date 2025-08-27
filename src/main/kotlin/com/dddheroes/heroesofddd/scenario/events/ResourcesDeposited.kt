package com.dddheroes.heroesofddd.scenario.events

data class ResourcesDeposited(
    override val resourcesPoolId: String,
    val resources: Map<String, Int>
) : ResourcesPoolEvent