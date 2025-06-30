package com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures

import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class IncreaseAvailableCreatures(
    val dwellingId: String,
    val creatureId: String,
    val increaseBy: Int,
)

private data class State(val isBuilt: Boolean, val availableCreatures: Int)

private val initialState = State(isBuilt = false, availableCreatures = 0)

private fun decide(
    command: IncreaseAvailableCreatures,
    state: State
): DwellingEvent {
    if (!state.isBuilt) {
        throw IllegalStateException("Only built dwelling can have available creatures")
    }

    // todo: check creatureId for the dwelling!

    return AvailableCreaturesChanged(
        dwellingId = command.dwellingId,
        creatureId = command.creatureId,
        changedBy = command.increaseBy,
        changedTo = state.availableCreatures + command.increaseBy
    )
}

private fun evolve(state: State, event: DwellingEvent): State = when (event) {
    is DwellingBuilt ->
        state.copy(isBuilt = true)
    is AvailableCreaturesChanged ->
        state.copy(availableCreatures = event.changedTo)
    else -> state
}


