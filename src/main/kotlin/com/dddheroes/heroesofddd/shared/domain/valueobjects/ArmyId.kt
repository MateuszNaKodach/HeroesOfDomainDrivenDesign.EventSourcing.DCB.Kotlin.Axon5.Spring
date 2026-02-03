package com.dddheroes.heroesofddd.shared.domain.valueobjects

import java.util.*

@JvmInline
value class ArmyId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Army id cannot be empty" }
    }

    companion object {
        fun random() = ArmyId(UUID.randomUUID().toString())
    }

}