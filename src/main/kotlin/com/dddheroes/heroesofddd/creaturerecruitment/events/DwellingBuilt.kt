package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.shared.domain.valueobjects.CreatureId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType

data class DwellingBuilt(
    override val dwellingId: DwellingId,
    val creatureId: CreatureId,
    val costPerTroop: Map<ResourceType, Int>
) : DwellingEvent
