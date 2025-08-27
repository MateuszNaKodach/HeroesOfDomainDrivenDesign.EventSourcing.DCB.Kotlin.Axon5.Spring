package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import org.axonframework.eventsourcing.annotations.EventTag

data class AvailableCreaturesChanged(
    @get:EventTag(EventTags.DWELLING_ID)
    override val dwellingId: String,
    val creatureId: String,
    val changedBy: Int,
    val changedTo: Int
) : DwellingEvent
