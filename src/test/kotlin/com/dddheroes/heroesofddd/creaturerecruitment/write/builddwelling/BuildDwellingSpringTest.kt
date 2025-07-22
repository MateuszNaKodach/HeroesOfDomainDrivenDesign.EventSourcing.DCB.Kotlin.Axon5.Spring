package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.configuration.ApplicationConfigurer
import org.axonframework.messaging.MetaData
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.MessagesRecordingConfigurationEnhancer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.util.*

@Disabled("Create context before running this test")
@SpringBootTest
internal class BuildDwellingSpringTest @Autowired constructor(configurer: ApplicationConfigurer) {

    private val fixture: AxonTestFixture = AxonTestFixture.with(configurer)
    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()


    @Test
    fun `given not built dwelling, when build, then built`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        fixture.given()
            .noPriorActivity()
            .`when`()
            .command(BuildDwelling(dwellingId, creatureId, costPerTroop), gameMetaData)
            .then()
            .events(
                DwellingBuilt(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    costPerTroop = costPerTroop
                )
            )
    }

    private val gameMetaData = MetaData.with("gameId", gameId)
        .and("playerId", playerId)

    @TestConfiguration
    class TestConfig {

        @Bean
        fun recordingEnhancer() = MessagesRecordingConfigurationEnhancer()
    }

}