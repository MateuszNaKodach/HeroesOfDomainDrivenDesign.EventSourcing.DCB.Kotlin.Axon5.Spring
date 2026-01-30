package com.dddheroes.heroesofddd.shared.application

import org.axonframework.extensions.kotlin.AxonMetadata

object GameMetadata {
    const val GAME_ID_KEY: String = "gameId"
    const val PLAYER_ID_KEY: String = "playerId"

    fun with(gameId: String): AxonMetadata {
        return with(gameId, "unknown")
    }

    fun with(gameId: String, playerId: String): AxonMetadata {
        return AxonMetadata.with(GAME_ID_KEY, gameId)
            .and(PLAYER_ID_KEY, playerId)
    }
}