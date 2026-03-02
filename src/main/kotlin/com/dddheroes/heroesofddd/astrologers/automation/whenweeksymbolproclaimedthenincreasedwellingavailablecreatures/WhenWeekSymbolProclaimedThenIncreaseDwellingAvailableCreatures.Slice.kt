package com.dddheroes.heroesofddd.astrologers.automation.whenweeksymbolproclaimedthenincreasedwellingavailablecreatures

import com.dddheroes.heroesofddd.astrologers.events.WeekSymbolProclaimed
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures.IncreaseAvailableCreatures
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.core.annotation.MetadataValue
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.axonframework.messaging.eventhandling.annotation.SequencingPolicy
import org.axonframework.messaging.eventhandling.sequencing.MetadataSequencingPolicy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

@Entity
@Table(
    name = "astrologers_automation_built_dwelling",
    indexes = [Index(name = "idx_astrologers_built_dwelling_game_creature", columnList = "gameId, creatureId")]
)
internal data class BuiltDwellingReadModel(
    val gameId: String,
    @Id
    val dwellingId: String,
    val creatureId: String
)

@ConditionalOnProperty(
    prefix = "slices.astrologers.automation",
    name = ["whenweeksymbolproclaimedthenincreasedwellingavailablecreatures.enabled"]
)
@Repository
private interface BuiltDwellingReadModelRepository : JpaRepository<BuiltDwellingReadModel, String> {
    fun findAllByGameIdAndCreatureId(gameId: String, creatureId: String): List<BuiltDwellingReadModel>
}

@ConditionalOnProperty(
    prefix = "slices.astrologers.automation",
    name = ["whenweeksymbolproclaimedthenincreasedwellingavailablecreatures.enabled"]
)
@Component
@SequencingPolicy(type = MetadataSequencingPolicy::class, parameters = ["gameId"])
private class WhenWeekSymbolProclaimedThenIncreaseDwellingAvailableCreaturesProcessor(
    private val commandGateway: CommandGateway,
    private val repository: BuiltDwellingReadModelRepository
) {

    @EventHandler
    fun react(
        event: WeekSymbolProclaimed,
        @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String,
        @MetadataValue(GameMetadata.PLAYER_ID_KEY) playerId: String
    ) {
        val increaseBy = event.growth
        repository.findAllByGameIdAndCreatureId(gameId, event.weekOf.raw)
            .forEach { dwelling -> increaseAvailableCreatures(dwelling, increaseBy, playerId) }
    }

    private fun increaseAvailableCreatures(dwelling: BuiltDwellingReadModel, increaseBy: Int, playerId: String) {
        val command = IncreaseAvailableCreatures(
            dwellingId = DwellingId(dwelling.dwellingId),
            creatureId = CreatureId(dwelling.creatureId),
            increaseBy = Quantity(increaseBy)
        )
        val metadata = GameMetadata.with(GameId(dwelling.gameId), PlayerId(playerId))
        commandGateway.send(command, metadata)
    }

    @EventHandler
    fun on(event: DwellingBuilt, @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String) {
        repository.save(
            BuiltDwellingReadModel(
                gameId = gameId,
                dwellingId = event.dwellingId.raw,
                creatureId = event.creatureId.raw
            )
        )
    }
}
