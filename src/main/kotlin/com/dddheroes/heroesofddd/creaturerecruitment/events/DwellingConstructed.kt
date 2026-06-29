package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.axonframework.messaging.eventhandling.annotation.Event

// Renamed from DwellingBuilt (1.0.0). v2 adds `level`; events stored under the old name/version
// are rewritten on read by the EventTransformerChain (see creaturerecruitment.transformation).
@Event(namespace = "CreatureRecruitment", name = "DwellingConstructed", version = "2.0.0")
data class DwellingConstructed(
    override val dwellingId: DwellingId,
    val creatureId: CreatureId,
    val costPerTroop: Resources,
    val level: Int
) : DwellingEvent
