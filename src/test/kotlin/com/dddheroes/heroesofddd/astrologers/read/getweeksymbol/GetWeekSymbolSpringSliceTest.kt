package com.dddheroes.heroesofddd.astrologers.read.getweeksymbol

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.astrologers.events.WeekSymbolProclaimed
import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.common.configuration.Configuration
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.TimeUnit

@TestPropertySource(properties = ["slices.astrologers.read.getweeksymbol.enabled=true"])
@HeroesAxonSpringBootTest
internal class GetWeekSymbolSpringSliceTest @Autowired constructor(
    private val fixture: AxonTestFixture
) {

    private val gameId = GameId.random()
    private val astrologersId = AstrologersId(gameId.raw)
    private val gameMetadata = AxonMetadata.with("gameId", gameId.raw)

    @Test
    fun `given no proclamation for queried week, then no result`() {
        fixture.When { nothing() } Then {
            expect { cfg ->
                val result = queryWeekSymbol(cfg, month = 1, week = 2)
                assertThat(result).isNull()
            }
        }
    }

    @Test
    fun `given week symbol proclaimed, then show symbol for that week`() {
        fixture.Given {
            event(WeekSymbolProclaimed(astrologersId, month = 1, week = 2, weekOf = CreatureId("angel"), growth = 5), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryWeekSymbol(cfg, month = 1, week = 2)
                assertThat(result).isEqualTo(
                    WeekSymbolReadModel(gameId.raw, month = 1, week = 2, weekOf = "angel", growth = 5)
                )
            }
        }
    }

    @Test
    fun `given multiple weeks proclaimed, then show symbol for queried week`() {
        fixture.Given {
            event(WeekSymbolProclaimed(astrologersId, month = 1, week = 1, weekOf = CreatureId("imp"), growth = 2), gameMetadata)
            event(WeekSymbolProclaimed(astrologersId, month = 1, week = 2, weekOf = CreatureId("angel"), growth = 5), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryWeekSymbol(cfg, month = 1, week = 2)
                assertThat(result).isEqualTo(
                    WeekSymbolReadModel(gameId.raw, month = 1, week = 2, weekOf = "angel", growth = 5)
                )
            }
        }
    }

    private fun queryWeekSymbol(cfg: Configuration, month: Int, week: Int): WeekSymbolReadModel? =
        cfg.getComponent(QueryGateway::class.java)
            .query(GetWeekSymbol(gameId, month, week), WeekSymbolReadModel::class.java)
            .orTimeout(1, TimeUnit.SECONDS)
            .join()
}
