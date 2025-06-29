package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import org.axonframework.eventsourcing.annotations.EventTag

data class AvailableCreaturesChanged(
    @EventTag(EventTags.DWELLING_ID)
    val dwellingId: String,
    val creatureId: String,
    val changedTo: Int
)
