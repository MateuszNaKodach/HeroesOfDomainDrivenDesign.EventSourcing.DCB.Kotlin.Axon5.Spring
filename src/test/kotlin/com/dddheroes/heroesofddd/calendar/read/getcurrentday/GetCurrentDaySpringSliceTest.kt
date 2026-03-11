package com.dddheroes.heroesofddd.calendar.read.getcurrentday

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.calendar.events.DayFinished
import com.dddheroes.heroesofddd.calendar.events.DayStarted
import com.dddheroes.heroesofddd.calendar.write.CalendarId
import com.dddheroes.heroesofddd.calendar.write.Day
import com.dddheroes.heroesofddd.calendar.write.Month
import com.dddheroes.heroesofddd.calendar.write.Week
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

@TestPropertySource(properties = ["slices.calendar.read.getcurrentday.enabled=true"])
@HeroesAxonSpringBootTest
internal class GetCurrentDaySpringSliceTest @Autowired constructor(
    private val fixture: AxonTestFixture
) {

    private val gameId = GameId.random()
    private val calendarId = CalendarId(gameId.raw)
    private val gameMetadata = AxonMetadata.with("gameId", gameId.raw)

    @Test
    fun `given no day started, then no result`() {
        fixture.When { nothing() } Then {
            expect { cfg ->
                val result = queryCurrentDay(cfg)
                assertThat(result).isNull()
            }
        }
    }

    @Test
    fun `given day started, then show current day in progress`() {
        fixture.Given {
            event(DayStarted(calendarId, Month(1), Week(1), Day(1)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryCurrentDay(cfg)
                assertThat(result).isEqualTo(
                    CurrentDayReadModel(gameId.raw, month = 1, week = 1, day = 1, finished = false)
                )
            }
        }
    }

    @Test
    fun `given multiple days started, then show the latest day`() {
        fixture.Given {
            event(DayStarted(calendarId, Month(1), Week(1), Day(1)), gameMetadata)
            event(DayStarted(calendarId, Month(1), Week(1), Day(2)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryCurrentDay(cfg)
                assertThat(result).isEqualTo(
                    CurrentDayReadModel(gameId.raw, month = 1, week = 1, day = 2, finished = false)
                )
            }
        }
    }

    @Test
    fun `given day started and finished, then show that day as finished`() {
        fixture.Given {
            event(DayStarted(calendarId, Month(1), Week(1), Day(1)), gameMetadata)
            event(DayFinished(calendarId, Month(1), Week(1), Day(1)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryCurrentDay(cfg)
                assertThat(result).isEqualTo(
                    CurrentDayReadModel(gameId.raw, month = 1, week = 1, day = 1, finished = true)
                )
            }
        }
    }

    @Test
    fun `given day finished and next day started, then show new day in progress`() {
        fixture.Given {
            event(DayStarted(calendarId, Month(1), Week(1), Day(1)), gameMetadata)
            event(DayFinished(calendarId, Month(1), Week(1), Day(1)), gameMetadata)
            event(DayStarted(calendarId, Month(1), Week(1), Day(2)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryCurrentDay(cfg)
                assertThat(result).isEqualTo(
                    CurrentDayReadModel(gameId.raw, month = 1, week = 1, day = 2, finished = false)
                )
            }
        }
    }

    private fun queryCurrentDay(cfg: Configuration): CurrentDayReadModel? =
        cfg.getComponent(QueryGateway::class.java)
            .query(GetCurrentDay(gameId), CurrentDayReadModel::class.java)
            .orTimeout(1, TimeUnit.SECONDS)
            .join()
}
