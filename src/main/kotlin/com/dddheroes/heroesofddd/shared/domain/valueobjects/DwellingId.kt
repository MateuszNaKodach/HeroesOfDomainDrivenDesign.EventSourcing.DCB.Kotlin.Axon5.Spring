package com.dddheroes.heroesofddd.shared.domain.valueobjects

import java.util.*

@JvmInline
value class DwellingId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Dwelling id cannot be empty" }
    }

    companion object {
        fun random() = DwellingId(UUID.randomUUID().toString())
    }

}