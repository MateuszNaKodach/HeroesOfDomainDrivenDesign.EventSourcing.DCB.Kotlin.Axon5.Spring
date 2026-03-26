package com.dddheroes.heroesofddd.calendar.read.getcurrentday

import com.dddheroes.heroesofddd.calendar.events.DayFinished
import com.dddheroes.heroesofddd.calendar.events.DayStarted
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.axonframework.messaging.core.annotation.MetadataValue
import org.axonframework.messaging.core.annotation.Namespace
import org.axonframework.messaging.core.annotation.SequencingPolicy
import org.axonframework.messaging.core.sequencing.MetadataSequencingPolicy
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler
import org.axonframework.messaging.queryhandling.annotation.Query
import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@Query(namespace = "Calendar", name = "GetCurrentDay", version = "1.0.0")
data class GetCurrentDay(val gameId: GameId)

@Entity
@Table(name = "calendar_read_getcurrentday")
data class CurrentDayReadModel(
    @Id val gameId: String,
    val month: Int,
    val week: Int,
    val day: Int,
    val finished: Boolean
)

@ConditionalOnProperty(prefix = "slices.calendar", name = ["read.getcurrentday.enabled"])
@Repository
private interface CurrentDayReadModelRepository : JpaRepository<CurrentDayReadModel, String>

@ConditionalOnProperty(prefix = "slices.calendar", name = ["read.getcurrentday.enabled"])
@Namespace("ReadModel_GetCurrentDay")
@Component
@SequencingPolicy(type = MetadataSequencingPolicy::class, parameters = ["gameId"])
private class CurrentDayReadModelProjector(
    val repository: CurrentDayReadModelRepository
) {

    @EventHandler
    fun on(event: DayStarted, @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String) {
        repository.save(
            CurrentDayReadModel(gameId, event.month.raw, event.week.raw, event.day.raw, finished = false)
        )
    }

    @EventHandler
    fun on(event: DayFinished, @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String) {
        repository.findByIdOrNull(gameId)?.let {
            repository.save(it.copy(finished = true))
        }
    }

    @ResetHandler
    fun onReset() {
        repository.deleteAll()
    }
}

@ConditionalOnProperty(prefix = "slices.calendar", name = ["read.getcurrentday.enabled"])
@Component
private class CurrentDayReadModelQueryHandler(
    private val repository: CurrentDayReadModelRepository
) {

    @QueryHandler
    fun handle(query: GetCurrentDay): CurrentDayReadModel? {
        return repository.findByIdOrNull(query.gameId.raw)
    }
}

@ConditionalOnProperty(prefix = "slices.calendar", name = ["read.getcurrentday.enabled"])
@RestController
@RequestMapping("games/{gameId}")
internal class GetCurrentDayRestApi(private val queryGateway: QueryGateway) {

    @GetMapping("/current-day")
    fun getCurrentDay(
        @PathVariable gameId: String
    ): CompletableFuture<ResponseEntity<CurrentDayReadModel>> = queryGateway.query(
        GetCurrentDay(GameId(gameId)),
        CurrentDayReadModel::class.java
    ).thenApply { result ->
        result?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }
}
