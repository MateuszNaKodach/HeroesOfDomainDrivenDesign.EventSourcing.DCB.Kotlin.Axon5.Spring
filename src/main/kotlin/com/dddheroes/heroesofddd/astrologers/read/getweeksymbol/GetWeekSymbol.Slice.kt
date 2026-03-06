package com.dddheroes.heroesofddd.astrologers.read.getweeksymbol

import com.dddheroes.heroesofddd.astrologers.events.WeekSymbolProclaimed
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
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
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.Serializable
import java.util.concurrent.CompletableFuture

data class GetWeekSymbol(val gameId: GameId, val month: Int, val week: Int)

data class WeekSymbolReadModelId(
    val gameId: String = "",
    val month: Int = 0,
    val week: Int = 0
) : Serializable

@Entity
@Table(name = "astrologers_read_getweeksymbol")
@IdClass(WeekSymbolReadModelId::class)
data class WeekSymbolReadModel(
    @Id val gameId: String,
    @Id val month: Int,
    @Id val week: Int,
    val weekOf: String,
    val growth: Int
)

@ConditionalOnProperty(prefix = "slices.astrologers", name = ["read.getweeksymbol.enabled"])
@Repository
private interface WeekSymbolReadModelRepository : JpaRepository<WeekSymbolReadModel, WeekSymbolReadModelId>

@ConditionalOnProperty(prefix = "slices.astrologers", name = ["read.getweeksymbol.enabled"])
@Component
@SequencingPolicy(type = MetadataSequencingPolicy::class, parameters = ["gameId"])
private class WeekSymbolReadModelProjector(
    val repository: WeekSymbolReadModelRepository
) {

    @EventHandler
    fun on(event: WeekSymbolProclaimed, @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String) {
        repository.save(
            WeekSymbolReadModel(gameId, event.month, event.week, event.weekOf.raw, event.growth)
        )
    }

    @ResetHandler
    fun onReset() {
        repository.deleteAll()
    }
}

@ConditionalOnProperty(prefix = "slices.astrologers", name = ["read.getweeksymbol.enabled"])
@Component
private class WeekSymbolReadModelQueryHandler(
    private val repository: WeekSymbolReadModelRepository
) {

    @QueryHandler
    fun handle(query: GetWeekSymbol): WeekSymbolReadModel? {
        val id = WeekSymbolReadModelId(query.gameId.raw, query.month, query.week)
        return repository.findByIdOrNull(id)
    }
}

@ConditionalOnProperty(prefix = "slices.astrologers", name = ["read.getweeksymbol.enabled"])
@RestController
@RequestMapping("games/{gameId}")
internal class GetWeekSymbolRestApi(private val queryGateway: QueryGateway) {

    @GetMapping("/week-symbol/{month}/{week}")
    fun getWeekSymbol(
        @PathVariable gameId: String,
        @PathVariable month: Int,
        @PathVariable week: Int
    ): CompletableFuture<ResponseEntity<WeekSymbolReadModel>> = queryGateway.query(
        GetWeekSymbol(GameId(gameId), month, week),
        WeekSymbolReadModel::class.java
    ).thenApply { result ->
        result?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }
}
