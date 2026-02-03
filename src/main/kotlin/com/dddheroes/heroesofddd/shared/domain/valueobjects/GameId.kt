package com.dddheroes.heroesofddd.shared.domain.valueobjects

import java.util.*

@JvmInline
value class GameId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Game ID cannot be empty" }
    }

    companion object {
        fun random() = GameId(UUID.randomUUID().toString())
    }

}