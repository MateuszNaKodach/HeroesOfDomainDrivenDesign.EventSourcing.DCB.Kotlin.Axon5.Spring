package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.application.gameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.creaturerecruitment.write.builddwelling.enabled=true"])
@HeroesAxonSpringBootTest
internal class BuildDwellingSpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()

    @Test
    fun `given not built dwelling, when build, then built`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest.given()
            .noPriorActivity()
            .`when`()
            .command(BuildDwelling(dwellingId, creatureId, costPerTroop), gameMetadata)
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .events(
                DwellingBuilt(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    costPerTroop = costPerTroop
                )
            )
            .eventsMatch { it.all { e -> e.gameMetadata == gameMetadata } }
    }

    @Test
    fun `given DwellingBuild, when BuildDwelling one more time, then nothing`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        // then
        sliceUnderTest
            .given()
            .events(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .`when`()
            .command(BuildDwelling(dwellingId, creatureId, costPerTroop))
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .noEvents()
    }

    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

}