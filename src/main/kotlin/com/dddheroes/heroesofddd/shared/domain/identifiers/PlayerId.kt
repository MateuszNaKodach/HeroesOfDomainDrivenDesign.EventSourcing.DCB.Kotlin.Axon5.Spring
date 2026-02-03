package com.dddheroes.heroesofddd.shared.domain.identifiers

import java.util.*

@JvmInline
value class PlayerId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Player ID cannot be empty" }
    }

    companion object {
        fun random() = PlayerId(UUID.randomUUID().toString())
        fun unknown() = PlayerId("unknown")
    }

}