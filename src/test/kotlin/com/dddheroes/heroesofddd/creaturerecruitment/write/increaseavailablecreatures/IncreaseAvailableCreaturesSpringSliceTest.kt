package com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.springTestFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["slices.creaturerecruitment.write.increaseavailablecreatures.enabled=true"])
@HeroesAxonSpringBootTest
internal class IncreaseAvailableCreaturesSpringSliceTest @Autowired constructor(configuration: AxonConfiguration) {

    private val sliceUnderTest: AxonTestFixture = springTestFixture(configuration)

    @Test
    fun `given DwellingBuild, when IncreaseAvailableCreatures, then exception`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("angel")

        sliceUnderTest
            .given()
            .noPriorActivity()
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy = Quantity(5)))
            .then()
            .resultMessagePayload(CommandHandlerResult.Failure("Only built dwelling can have available creatures"))
    }

    @Test
    fun `given DwellingBuilt, when IncreaseAvailableCreatures, then AvailableCreaturesChanged`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val increaseBy = Quantity(3)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy))
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .events(
                AvailableCreaturesChanged(
                    dwellingId,
                    creatureId,
                    changedBy = increaseBy.raw,
                    changedTo = increaseBy
                )
            )
    }

    @Test
    fun `given DwellingBuilt with AvailableCreaturesChanged, when IncreaseAvailableCreatures, then AvailableCreaturesChanged`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = Quantity(1)))
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy = Quantity(2)))
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .events(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 2, changedTo = Quantity(3)))
    }

}