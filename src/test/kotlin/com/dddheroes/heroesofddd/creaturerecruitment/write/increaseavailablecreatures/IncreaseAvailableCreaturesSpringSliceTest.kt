package com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures

import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.common.configuration.ApplicationConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.MessagesRecordingConfigurationEnhancer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.util.*

@SpringBootTest
internal class IncreaseAvailableCreaturesSpringSliceTest @Autowired constructor(configurer: ApplicationConfigurer) {

    private val sliceUnderTest: AxonTestFixture = AxonTestFixture.with(configurer)

    @Test
    fun `given DwellingBuild, when IncreaseAvailableCreatures, then exception`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"

        // then
        sliceUnderTest
            .given()
            .noPriorActivity()
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy = 5))
            .then()
            .exceptionSatisfies { ex -> assertThat(ex).hasMessageContaining("Only built dwelling can have available creatures") }
    }

    @Test
    fun `given DwellingBuilt, when IncreaseAvailableCreatures, then AvailableCreaturesChanged`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val increaseBy = 3

        // then
        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy))
            .then()
            .events(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = increaseBy, changedTo = increaseBy))
    }

    @Test
    fun `given DwellingBuilt with AvailableCreaturesChanged, when IncreaseAvailableCreatures, then AvailableCreaturesChanged`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        // then
        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = 1))
            .`when`()
            .command(IncreaseAvailableCreatures(dwellingId, creatureId, increaseBy = 2))
            .then()
            .events(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 2, changedTo = 3))
    }

    @TestConfiguration
    class TestConfig {

        @Bean
        fun recordingEnhancer() = MessagesRecordingConfigurationEnhancer()
    }

}