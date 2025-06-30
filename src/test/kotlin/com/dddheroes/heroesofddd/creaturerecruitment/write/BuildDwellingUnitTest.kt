package com.dddheroes.heroesofddd.creaturerecruitment.write

import com.dddheroes.heroesofddd.creaturerecruitment.UnitTestAxonApplication
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import java.util.*

internal class BuildDwellingUnitTest {

    private val fixture: AxonTestFixture = AxonTestFixture.with(
        UnitTestAxonApplication.configurer(
            { registerStatefulCommandHandlingModule {BuildDwellingWriteSliceConfig().module()} },
            configOverride = { axonServerEnabled = false }
        ))

    @Test
    fun `given not built dwelling, when build, then built`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf("gold" to 3000, "gems" to 1)

        fixture.given()
            .noPriorActivity()
            .`when`()
            .command(BuildDwelling(dwellingId, creatureId, costPerTroop))
            .then()
            .events(
                DwellingBuilt(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    costPerTroop = costPerTroop
                )
            )
    }

}