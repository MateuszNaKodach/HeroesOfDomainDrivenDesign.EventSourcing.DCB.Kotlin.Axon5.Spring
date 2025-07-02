package com.dddheroes.heroesofddd.shared.application

import org.axonframework.messaging.MetaData

object GameMetaData {
    const val GAME_ID_KEY: String = "gameId"
    const val PLAYER_ID_KEY: String = "playerId"

    fun with(gameId: String): MetaData {
        return with(gameId, "unknown")
    }

    fun with(gameId: String, playerId: String): MetaData {
        return MetaData.with(GAME_ID_KEY, gameId)
            .and(PLAYER_ID_KEY, playerId)
    }
}