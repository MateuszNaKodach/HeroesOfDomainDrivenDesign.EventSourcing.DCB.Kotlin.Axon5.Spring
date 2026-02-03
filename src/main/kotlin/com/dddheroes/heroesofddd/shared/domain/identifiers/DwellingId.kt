package com.dddheroes.heroesofddd.shared.domain.identifiers

import java.util.*

@JvmInline
value class DwellingId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Dwelling ID cannot be empty" }
    }

    companion object {
        fun random() = DwellingId(UUID.randomUUID().toString())
    }

}