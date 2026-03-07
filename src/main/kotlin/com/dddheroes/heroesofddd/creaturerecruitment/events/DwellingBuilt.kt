package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.axonframework.messaging.eventhandling.annotation.Event

@Event(namespace = "CreatureRecruitment", name = "DwellingBuilt", version = "1.0.0")
data class DwellingBuilt(
    override val dwellingId: DwellingId,
    val creatureId: CreatureId,
    val costPerTroop: Resources
) : DwellingEvent
