package com.dddheroes.heroesofddd.resourcespool.write.withdrawresources

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesDeposited
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesWithdrawn
import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult.Failure
import com.dddheroes.sdk.application.CommandHandlerResult.Success
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.resourcespool.write.withdrawresources.enabled=true"])
@HeroesAxonSpringBootTest
internal class WithdrawResourcesSpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    private val resourcesPoolId = ResourcesPoolId.random()

    @Test
    fun `given empty pool, when withdraw resources, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(
                    WithdrawResources(resourcesPoolId, Resources.of(ResourceType.GOLD to 1000)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Failure("Cannot withdraw more than deposited resources"))
                noEvents()
            }
        }
    }

    @Test
    fun `given sufficient deposit, when withdraw, then success`() {
        sliceUnderTest.Scenario {
            Given {
                event(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.GOLD to 1000, ResourceType.WOOD to 10)), gameMetadata)
            } When {
                command(
                    WithdrawResources(resourcesPoolId, Resources.of(ResourceType.WOOD to 10)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(ResourcesWithdrawn(resourcesPoolId, Resources.of(ResourceType.WOOD to 10)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given deposit, when withdraw more than deposited, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                event(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.WOOD to 10)), gameMetadata)
            } When {
                command(
                    WithdrawResources(resourcesPoolId, Resources.of(ResourceType.WOOD to 12)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Failure("Cannot withdraw more than deposited resources"))
                noEvents()
            }
        }
    }

    @Test
    fun `given deposit and partial withdrawal, when withdraw remaining, then success`() {
        sliceUnderTest.Scenario {
            Given {
                event(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.GOLD to 1000)), gameMetadata)
                event(ResourcesWithdrawn(resourcesPoolId, Resources.of(ResourceType.GOLD to 400)), gameMetadata)
            } When {
                command(
                    WithdrawResources(resourcesPoolId, Resources.of(ResourceType.GOLD to 600)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(ResourcesWithdrawn(resourcesPoolId, Resources.of(ResourceType.GOLD to 600)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given deposit and partial withdrawal, when withdraw more than remaining, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                event(ResourcesDeposited(resourcesPoolId, Resources.of(ResourceType.GOLD to 1000)), gameMetadata)
                event(ResourcesWithdrawn(resourcesPoolId, Resources.of(ResourceType.GOLD to 400)), gameMetadata)
            } When {
                command(
                    WithdrawResources(resourcesPoolId, Resources.of(ResourceType.GOLD to 601)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Failure("Cannot withdraw more than deposited resources"))
                noEvents()
            }
        }
    }
}
