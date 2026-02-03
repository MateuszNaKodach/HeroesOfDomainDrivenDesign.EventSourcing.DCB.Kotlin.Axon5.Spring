package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.creaturerecruitment.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType

data class DwellingBuilt(
    override val dwellingId: DwellingId,
    val creatureId: String,
    val costPerTroop: Map<ResourceType, Int>
) : DwellingEvent
