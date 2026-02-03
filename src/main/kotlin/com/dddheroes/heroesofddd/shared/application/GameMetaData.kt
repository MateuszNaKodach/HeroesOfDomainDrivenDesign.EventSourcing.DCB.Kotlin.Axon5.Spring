package com.dddheroes.heroesofddd.shared.application

import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import org.axonframework.extensions.kotlin.AxonMetadata

object GameMetadata {
    const val GAME_ID_KEY: String = "gameId"
    const val PLAYER_ID_KEY: String = "playerId"

    fun with(gameId: GameId): AxonMetadata {
        return with(gameId, PlayerId.unknown())
    }

    fun with(gameId: GameId, playerId: PlayerId): AxonMetadata {
        return AxonMetadata.with(GAME_ID_KEY, gameId.raw)
            .and(PLAYER_ID_KEY, playerId.raw)
    }
}