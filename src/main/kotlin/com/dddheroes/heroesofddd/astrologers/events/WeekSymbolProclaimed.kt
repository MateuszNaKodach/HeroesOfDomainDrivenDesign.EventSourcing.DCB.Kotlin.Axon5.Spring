package com.dddheroes.heroesofddd.astrologers.events

import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId

data class WeekSymbolProclaimed(
    override val astrologersId: AstrologersId,
    val month: Int,
    val week: Int,
    val weekOf: CreatureId,
    val growth: Int
) : AstrologersEvent
