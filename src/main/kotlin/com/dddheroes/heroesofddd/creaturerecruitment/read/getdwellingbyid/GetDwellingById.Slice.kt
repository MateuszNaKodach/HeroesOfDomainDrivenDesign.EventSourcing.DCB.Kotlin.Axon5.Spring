package com.dddheroes.heroesofddd.creaturerecruitment.read.getdwellingbyid

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

data class GetDwellingById(
    val gameId: GameId,
    val dwellingId: DwellingId
) {
    data class Result(
        val dwellingId: String,
        val creatureId: String,
        val costPerTroop: Map<String, Int>,
        val availableCreatures: Int
    )
}

val initialState = GetDwellingById.Result("", "", emptyMap(), 0)

private fun evolve(state: GetDwellingById.Result, event: DwellingEvent): GetDwellingById.Result = when (event) {
    is DwellingBuilt ->
        state.copy(
            dwellingId = event.dwellingId.raw,
            creatureId = event.creatureId.raw,
            costPerTroop = event.costPerTroop.raw.entries
                .associate { it.key.name to it.value.raw },
            availableCreatures = 0
        )

    is AvailableCreaturesChanged ->
        state.copy(availableCreatures = event.changedTo.raw)

    else -> state
}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["read.getdwellingbyid.enabled"])
@EventSourced(tagKey = EventTags.DWELLING_ID)
private class DwellingInlineProjection private constructor(val state: GetDwellingById.Result) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = DwellingInlineProjection(
        evolve(state, event)
    )

    @EventSourcingHandler
    fun evolve(event: AvailableCreaturesChanged) = DwellingInlineProjection(
        evolve(state, event)
    )
}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["read.getdwellingbyid.enabled"])
@Component
private class GetDwellingByIdQueryHandler {

    @QueryHandler
    fun handle(
        query: GetDwellingById,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) dwelling: DwellingInlineProjection
    ): GetDwellingById.Result? = dwelling.state.takeIf { it != initialState }

}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["read.getdwellingbyid.enabled"])
@RestController
@RequestMapping("games/{gameId}")
internal class GetDwellingByIdRestApi(private val queryGateway: QueryGateway) {

    @GetMapping("/dwellings/{dwellingId}")
    fun getDwellingById(
        @PathVariable gameId: String,
        @PathVariable dwellingId: String,
    ): CompletableFuture<GetDwellingById.Result> = queryGateway.query(
        GetDwellingById(GameId(gameId), DwellingId(dwellingId)),
        GetDwellingById.Result::class.java
    )
}
