package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.configuration.ApplicationConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.*

@Disabled("ClassCastException")
@SpringBootTest
internal class BuildDwellingSpringTest @Autowired constructor(configurer: ApplicationConfigurer) {

    // fixme: class org.axonframework.commandhandling.SimpleCommandBus cannot be cast to class org.axonframework.test.fixture.RecordingCommandBus
    private val fixture: AxonTestFixture = AxonTestFixture.with(configurer)

    @Test
    fun `given not built dwelling, when build, then built`() {
        val dwellingId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

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