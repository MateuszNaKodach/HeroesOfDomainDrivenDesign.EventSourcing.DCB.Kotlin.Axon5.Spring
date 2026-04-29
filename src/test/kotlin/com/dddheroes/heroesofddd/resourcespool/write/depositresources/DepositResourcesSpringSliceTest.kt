package com.dddheroes.heroesofddd.resourcespool.write.depositresources

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesDeposited
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesWithdrawn
import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult.Success
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.resourcespool.write.depositresources.enabled=true"])
@HeroesAxonSpringBootTest
internal class DepositResourcesSpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    private val resourcesPoolId = ResourcesPoolId.random()

    @Test
    fun `given empty pool, when deposit resources, then success`() {
        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(
                    DepositResources(resourcesPoolId, Resources.of(ResourceType.GOLD to 1000)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.GOLD to 1000)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given already deposited resources, when deposit more, then success`() {
        sliceUnderTest.Scenario {
            Given {
                event(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.GOLD to 1000)), gameMetadata)
            } When {
                command(
                    DepositResources(resourcesPoolId, Resources.of(ResourceType.WOOD to 10)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.WOOD to 10)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given deposited and then withdrawn resources, when deposit again, then success`() {
        sliceUnderTest.Scenario {
            Given {
                event(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.GOLD to 1000)), gameMetadata)
                event(ResourcesWithdrawn(resourcesPoolId, Resources.of(ResourceType.GOLD to 400)), gameMetadata)
            } When {
                command(
                    DepositResources(resourcesPoolId, Resources.of(ResourceType.GOLD to 500)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.GOLD to 500)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }
}
