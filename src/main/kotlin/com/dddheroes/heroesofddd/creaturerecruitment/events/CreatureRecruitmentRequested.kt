package com.dddheroes.heroesofddd.creaturerecruitment.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.*
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.axonframework.eventsourcing.annotation.EventTag
import org.axonframework.messaging.eventhandling.annotation.Event

@Event(namespace = "CreatureRecruitment", name = "CreatureRecruitmentRequested", version = "1.0.0")
data class CreatureRecruitmentRequested(
    @get:EventTag(EventTags.RECRUITMENT_ID)
    val recruitmentId: RecruitmentId,
    val dwellingId: DwellingId,
    val resourcesPoolId: ResourcesPoolId,
    val creatureId: CreatureId,
    val armyId: ArmyId,
    val quantity: Quantity,
    val expectedCost: Resources
) : HeroesEvent
