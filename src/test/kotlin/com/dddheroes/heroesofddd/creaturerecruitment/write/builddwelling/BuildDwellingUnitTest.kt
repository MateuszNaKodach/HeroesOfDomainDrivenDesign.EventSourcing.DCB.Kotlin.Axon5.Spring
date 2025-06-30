package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.heroesofddd.creaturerecruitment.UnitTestAxonApplication
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import java.util.*

internal class BuildDwellingUnitTest {

    private val sliceUnderTest = AxonTestFixture.with(
        UnitTestAxonApplication.configurer(
            { registerStatefulCommandHandlingModule { BuildDwellingWriteSliceConfig().module() } },
            { axonServerEnabled = false }
        ))

    @Test
    fun `given not built dwelling, when build, then built`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf("gold" to 3000, "gems" to 1)

        sliceUnderTest
            .given()
            .noPriorActivity()
            .`when`()
            .command(BuildDwelling(dwellingId, creatureId, costPerTroop))
            .then()
            .events(DwellingBuilt(dwellingId, creatureId, costPerTroop))
    }

    @Test
    fun `given DwellingBuild, when BuildDwelling one more time, then exception`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf("gold" to 3000, "gems" to 1)

        // then
        sliceUnderTest
            .given()
            .events(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .`when`()
            .command(BuildDwelling(dwellingId, creatureId, costPerTroop))
            .then()
            .noEvents()
    }
}
