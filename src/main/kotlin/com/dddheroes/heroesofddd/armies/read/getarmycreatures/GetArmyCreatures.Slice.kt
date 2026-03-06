package com.dddheroes.heroesofddd.armies.read.getarmycreatures

import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.axonframework.messaging.core.annotation.MetadataValue
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.axonframework.messaging.eventhandling.annotation.SequencingPolicy
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler
import org.axonframework.messaging.eventhandling.sequencing.MetadataSequencingPolicy
import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.Serializable
import java.util.concurrent.CompletableFuture

data class GetArmyCreatures(
    val gameId: GameId,
    val armyId: ArmyId
) {
    data class Result(val armyId: String, val stacks: List<CreatureStack>)
    data class CreatureStack(val creatureId: String, val quantity: Int)
}

data class CreatureStackReadModelId(
    val gameId: String = "",
    val armyId: String = "",
    val creatureId: String = ""
) : Serializable

@Entity
@Table(
    name = "armies_read_getarmycreatures",
    indexes = [Index(name = "idx_army_creatures_game_army", columnList = "gameId, armyId")]
)
@IdClass(CreatureStackReadModelId::class)
data class CreatureStackReadModel(
    @Id val gameId: String,
    @Id val armyId: String,
    @Id val creatureId: String,
    val quantity: Int = 0
)

@ConditionalOnProperty(prefix = "slices.armies", name = ["read.getarmycreatures.enabled"])
@Repository
private interface CreatureStackReadModelRepository : JpaRepository<CreatureStackReadModel, CreatureStackReadModelId> {
    fun findAllByGameIdAndArmyId(gameId: String, armyId: String): List<CreatureStackReadModel>
}

@ConditionalOnProperty(prefix = "slices.armies", name = ["read.getarmycreatures.enabled"])
@Component
@SequencingPolicy(type = MetadataSequencingPolicy::class, parameters = ["gameId"])
private class CreatureStackReadModelProjector(
    val repository: CreatureStackReadModelRepository
) {

    @EventHandler
    fun on(event: CreatureAddedToArmy, @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String) {
        val id = CreatureStackReadModelId(gameId, event.armyId.raw, event.creatureId.raw)
        val updated = repository.findByIdOrNull(id)
            ?.let { it.copy(quantity = it.quantity + event.quantity.raw) }
            ?: CreatureStackReadModel(gameId, event.armyId.raw, event.creatureId.raw, event.quantity.raw)
        repository.save(updated)
    }

    @EventHandler
    fun on(event: CreatureRemovedFromArmy, @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String) {
        val id = CreatureStackReadModelId(gameId, event.armyId.raw, event.creatureId.raw)
        repository.findByIdOrNull(id)?.let { existing ->
            val newQuantity = existing.quantity - event.quantity.raw
            if (newQuantity <= 0) repository.deleteById(id) else repository.save(existing.copy(quantity = newQuantity))
        }
    }

    @ResetHandler
    fun onReset() {
        repository.deleteAll()
    }
}

@ConditionalOnProperty(prefix = "slices.armies", name = ["read.getarmycreatures.enabled"])
@Component
private class CreatureStackReadModelQueryHandler(
    private val repository: CreatureStackReadModelRepository
) {

    @QueryHandler
    fun handle(query: GetArmyCreatures): GetArmyCreatures.Result {
        val stacks = repository.findAllByGameIdAndArmyId(query.gameId.raw, query.armyId.raw)
            .map { GetArmyCreatures.CreatureStack(it.creatureId, it.quantity) }
        return GetArmyCreatures.Result(query.armyId.raw, stacks)
    }
}

@ConditionalOnProperty(prefix = "slices.armies", name = ["read.getarmycreatures.enabled"])
@RestController
@RequestMapping("games/{gameId}")
internal class GetArmyCreaturesRestApi(private val queryGateway: QueryGateway) {

    @GetMapping("/armies/{armyId}/creatures")
    fun getArmyCreatures(
        @PathVariable gameId: String,
        @PathVariable armyId: String
    ): CompletableFuture<GetArmyCreatures.Result> = queryGateway.query(
        GetArmyCreatures(GameId(gameId), ArmyId(armyId)),
        GetArmyCreatures.Result::class.java
    )
}
