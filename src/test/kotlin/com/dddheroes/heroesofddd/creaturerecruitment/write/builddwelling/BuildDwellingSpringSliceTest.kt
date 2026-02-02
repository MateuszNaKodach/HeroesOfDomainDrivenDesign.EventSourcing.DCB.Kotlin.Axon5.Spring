package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.extension.spring.test.AxonSpringBootTest
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.springTestFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

@AxonSpringBootTest
internal class BuildDwellingSpringSliceTest @Autowired constructor(configuration: AxonConfiguration) {

    private val sliceUnderTest: AxonTestFixture = springTestFixture(configuration)

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()

    @Test
    fun `given not built dwelling, when build, then built`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest.given()
            .noPriorActivity()
            .`when`()
            .command(BuildDwelling(dwellingId, creatureId, costPerTroop), gameMetadata)
            .then()
            .events(
                DwellingBuilt(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    costPerTroop = costPerTroop
                )
            )
    }

    @Test
    fun `given DwellingBuild, when BuildDwelling one more time, then nothing`() {
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

    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

}