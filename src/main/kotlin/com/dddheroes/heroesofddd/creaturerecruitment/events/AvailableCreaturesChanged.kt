package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import org.axonframework.eventsourcing.annotation.EventTag
import org.axonframework.messaging.eventhandling.annotation.Event

@Event(namespace = "CreatureRecruitment", name = "AvailableCreaturesChanged", version = "1.0.0")
data class AvailableCreaturesChanged(
    @get:EventTag(EventTags.DWELLING_ID)
    override val dwellingId: DwellingId,
    val creatureId: CreatureId,
    val changedBy: Int,
    val changedTo: Quantity
) : DwellingEvent
