package com.dddheroes.heroesofddd.scenario.write.increaseavailablecreatures

import com.dddheroes.heroesofddd.scenario.UnitTestAxonApplication
import com.dddheroes.heroesofddd.scenario.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.scenario.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import java.util.*

internal class IncreaseAvailableCreaturesUnitTest {

    private val sliceUnderTest = AxonTestFixture.with(
        UnitTestAxonApplication.configurer(
            { registerStatefulCommandHandlingModule { IncreaseAvailableCreaturesWriteSliceConfig().increaseAvailableCreaturesSlice() } },
            { axonServerEnabled = false }
        ))

    @Test
    fun `given DwellingBuild, when IncreaseAvailableCreatures, then exception`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"

        // then
        sliceUnderTest
            .given()
            .noPriorActivity()
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy = 5))
            .then()
            .exception(IllegalStateException::class.java, "Only built dwelling can have available creatures")
    }

    @Test
    fun `given DwellingBuilt, when IncreaseAvailableCreatures, then AvailableCreatuesChanged`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val increaseBy = 3

        // then
        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy))
            .then()
            .events(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = increaseBy, changedTo = increaseBy))
    }

    @Test
    fun `given DwellingBuilt with AvailableCreatuesChanged, when IncreaseAvailableCreatures, then AvailableCreatuesChanged`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        // then
        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = 1))
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy = 2))
            .then()
            .events(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 2, changedTo = 3))
    }

}