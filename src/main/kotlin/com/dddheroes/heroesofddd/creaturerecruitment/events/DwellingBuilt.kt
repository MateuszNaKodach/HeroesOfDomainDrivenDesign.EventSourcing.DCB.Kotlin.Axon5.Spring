package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.shared.domain.valueobjects.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType

data class DwellingBuilt(
    @get:JvmName("dwellingId")
    override val dwellingId: DwellingId,
    val creatureId: String,
    val costPerTroop: Map<ResourceType, Int>
) : DwellingEvent
