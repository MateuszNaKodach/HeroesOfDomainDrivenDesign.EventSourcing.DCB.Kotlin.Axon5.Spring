package com.dddheroes.heroesofddd.creaturerecruitment.write.requestcreaturerecruitment

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruitmentRequested
import com.dddheroes.heroesofddd.shared.domain.identifiers.*
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult.Success
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.creaturerecruitment.write.requestcreaturerecruitment.enabled=true"])
@HeroesAxonSpringBootTest
internal class RequestCreatureRecruitmentSpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    @Test
    fun `when request creature recruitment, then CreatureRecruitmentRequested event emitted`() {
        val recruitmentId = RecruitmentId.random()
        val dwellingId = DwellingId.random()
        val resourcesPoolId = ResourcesPoolId(playerId)
        val creatureId = CreatureId("angel")
        val armyId = ArmyId.random()
        val quantity = Quantity(2)
        val expectedCost = Resources.of(ResourceType.GOLD to 6000, ResourceType.GEMS to 2)

        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(
                    RequestCreatureRecruitment(
                        recruitmentId = recruitmentId,
                        dwellingId = dwellingId,
                        resourcesPoolId = resourcesPoolId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = quantity,
                        expectedCost = expectedCost
                    ),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(
                    CreatureRecruitmentRequested(
                        recruitmentId = recruitmentId,
                        dwellingId = dwellingId,
                        resourcesPoolId = resourcesPoolId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = quantity,
                        expectedCost = expectedCost
                    )
                )
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }
}
