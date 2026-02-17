package com.dddheroes.heroesofddd.astrologers.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import org.axonframework.eventsourcing.annotation.EventTag

sealed interface AstrologersEvent : HeroesEvent {
    @get:EventTag(EventTags.ASTROLOGERS_ID)
    val astrologersId: AstrologersId
}
