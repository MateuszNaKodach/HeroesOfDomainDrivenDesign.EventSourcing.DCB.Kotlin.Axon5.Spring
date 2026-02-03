package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.DwellingId
import org.axonframework.eventsourcing.annotation.EventTag

data class AvailableCreaturesChanged(
    @get:EventTag(EventTags.DWELLING_ID)
    override val dwellingId: DwellingId,
    val creatureId: String,
    val changedBy: Int,
    val changedTo: Int
) : DwellingEvent
