package com.dddheroes.heroesofddd.shared.domain.identifiers

import java.util.*

@JvmInline
value class ArmyId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Army ID cannot be empty" }
    }

    companion object {
        fun random() = ArmyId(UUID.randomUUID().toString())
    }

}