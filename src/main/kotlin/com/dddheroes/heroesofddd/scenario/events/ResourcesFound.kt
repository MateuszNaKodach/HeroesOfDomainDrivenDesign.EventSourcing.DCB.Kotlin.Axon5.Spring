package com.dddheroes.heroesofddd.scenario.events

import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType

data class ResourcesFound(
    val resourcesId: String,
    override val resourcesPoolId: String,
    override val gained: Map<ResourceType, Int>
) : MapEvent, ResourcesIncomeEvent