package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources

data class DwellingBuilt(
    override val dwellingId: DwellingId,
    val creatureId: CreatureId,
    val costPerTroop: Resources
) : DwellingEvent
