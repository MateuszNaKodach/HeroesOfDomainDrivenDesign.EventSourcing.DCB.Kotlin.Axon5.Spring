package com.dddheroes.heroesofddd.creaturerecruitment.read.getalldwellings

import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.axonframework.messaging.core.annotation.MetadataValue
import org.axonframework.messaging.core.annotation.Namespace
import org.axonframework.messaging.core.annotation.SequencingPolicy
import org.axonframework.messaging.core.sequencing.MetadataSequencingPolicy
import org.axonframework.messaging.deadletter.DeadLetter
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken
import org.axonframework.messaging.eventhandling.replay.ReplayStatus
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler
import org.axonframework.messaging.queryhandling.annotation.Query
import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@Query(namespace = "CreatureRecruitment", name = "GetAllDwellings", version = "1.0.0")
data class GetAllDwellings(
    val gameId: GameId
) {
    data class Result(val items: List<DwellingReadModel>)
}

@Entity
@Table(name = "creaturerecruitment_read_getalldwellings")
data class DwellingReadModel(
    val gameId: String,
    @Id
    val dwellingId: String,
    val creatureId: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val costPerTroop: Map<String, Int> = emptyMap(),
    val availableCreatures: Int = 0
)

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["read.getalldwellings.enabled"])
@Repository
private interface DwellingReadModelRepository : JpaRepository<DwellingReadModel, String> {
    fun findAllByGameId(gameId: String): List<DwellingReadModel>
}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["read.getalldwellings.enabled"])
@Namespace("ReadModel_GetAllDwellings")
@Component
@SequencingPolicy(type = MetadataSequencingPolicy::class, parameters = ["gameId"])
private class DwellingReadModelProjector(
    val repository: DwellingReadModelRepository
) {

    @EventHandler
    fun on(event: DwellingBuilt, @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String) {
        val state = DwellingReadModel(
            gameId,
            event.dwellingId.raw,
            event.creatureId.raw,
            event.costPerTroop.raw.entries
                .associate { it.key.name to it.value.raw },
            0
        )
        repository.save(state)
    }

    @EventHandler
    fun on(
        event: AvailableCreaturesChanged,
        @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String,
        replayStatus: ReplayStatus,
        trackingToken: TrackingToken?,
        deadLetter: DeadLetter<EventMessage>?
    ) {
        // TODO: remove — temporary DLQ/parameter injection testing
        log.info(
            "AvailableCreaturesChanged for gameId={}, replayStatus={}, trackingToken={}, deadLetter={}",
            gameId, replayStatus, trackingToken, deadLetter?.let {
                "DeadLetter(cause=${it.cause().orElse(null)}, enqueuedAt=${it.enqueuedAt()}, lastTouched=${it.lastTouched()}, diagnostics=${it.diagnostics()})"
            }
        )
        if (gameId.startsWith("fail-")) {
            throw RuntimeException("DLQ test: simulated failure for gameId=$gameId")
        }
        val dwellingId = event.dwellingId.raw
        val state = repository.findByIdOrNull(dwellingId)
        if (state != null) {
            val updatedState = state.copy(availableCreatures = event.changedTo.raw)
            repository.save(updatedState)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DwellingReadModelProjector::class.java)
    }

    @ResetHandler
    fun onReset() {
        repository.deleteAll();
    }

}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["read.getalldwellings.enabled"])
@Component
private class GetAllDwellingsQueryHandler(
    private val repository: DwellingReadModelRepository
) {

    @QueryHandler
    fun handle(query: GetAllDwellings): GetAllDwellings.Result {
        val readModel = repository.findAllByGameId(query.gameId.raw)
        return GetAllDwellings.Result(readModel)
    }
}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["read.getalldwellings.enabled"])
@RestController
@RequestMapping("games/{gameId}")
internal class GetAllDwellingsRestApi(private val queryGateway: QueryGateway) {

    @GetMapping("/dwellings")
    fun getDwellings(
        @PathVariable gameId: String,
    ): CompletableFuture<GetAllDwellings.Result> = queryGateway.query(
        GetAllDwellings(GameId(gameId)),
        GetAllDwellings.Result::class.java
    )
}
