package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.heroesofddd.creaturerecruitment.UnitTestAxonApplication
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import java.util.*

internal class BuildDwellingUnitTest {

    private val sliceUnderTest = AxonTestFixture.with(
        UnitTestAxonApplication.configurer(
            { registerCommandHandlingModule { BuildDwellingWriteSliceConfig().buildDwellingSlice() } },
            { axonServerEnabled = false }
        ))

    @Test
    fun `given not built dwelling, when build, then built`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

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
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

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
