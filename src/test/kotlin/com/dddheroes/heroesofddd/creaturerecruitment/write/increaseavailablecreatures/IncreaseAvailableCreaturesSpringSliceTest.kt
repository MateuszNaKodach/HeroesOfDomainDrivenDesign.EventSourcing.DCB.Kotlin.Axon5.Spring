package com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures

import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.extensions.spring.test.AxonSpringBootTest
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.creaturerecruitment.write.increaseavailablecreatures.enabled=true"])
@AxonSpringBootTest
internal class IncreaseAvailableCreaturesSpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    @Test
    fun `given DwellingBuild, when IncreaseAvailableCreatures, then exception`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("angel")

        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy = Quantity(5)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Only built dwelling can have available creatures"))
            }
        }
    }

    @Test
    fun `given DwellingBuilt, when IncreaseAvailableCreatures, then AvailableCreaturesChanged`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val increaseBy = Quantity(3)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
            } When {
                command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(
                    AvailableCreaturesChanged(
                        dwellingId,
                        creatureId,
                        changedBy = increaseBy.raw,
                        changedTo = increaseBy
                    )
                )
            }
        }
    }

    @Test
    fun `given DwellingBuilt with AvailableCreaturesChanged, when IncreaseAvailableCreatures, then AvailableCreaturesChanged`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = Quantity(1)), gameMetadata)
            } When {
                command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy = Quantity(2)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 2, changedTo = Quantity(3)))
            }
        }
    }

}
