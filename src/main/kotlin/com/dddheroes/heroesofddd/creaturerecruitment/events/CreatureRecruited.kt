package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.axonframework.eventsourcing.annotation.EventTag
import org.axonframework.messaging.eventhandling.annotation.Event

@Event(namespace = "CreatureRecruitment", name = "CreatureRecruited", version = "1.0.0")
data class CreatureRecruited(
    override val dwellingId: DwellingId,
    val creatureId: CreatureId,
    @EventTag(EventTags.ARMY_ID)
    val toArmy: ArmyId,
    val quantity: Quantity,
    val totalCost: Resources
) : DwellingEvent
