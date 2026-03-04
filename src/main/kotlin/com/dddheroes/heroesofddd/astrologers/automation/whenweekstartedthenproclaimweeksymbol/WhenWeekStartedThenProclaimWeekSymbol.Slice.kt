package com.dddheroes.heroesofddd.astrologers.automation.whenweekstartedthenproclaimweeksymbol

import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.astrologers.write.MonthWeek
import com.dddheroes.heroesofddd.astrologers.write.proclaimweeksymbol.ProclaimWeekSymbol
import com.dddheroes.heroesofddd.calendar.events.DayStarted
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import org.axonframework.messaging.commandhandling.gateway.CommandDispatcher
import org.axonframework.messaging.core.annotation.MetadataValue
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

private const val FIRST_DAY_OF_THE_WEEK = 1

@ConditionalOnProperty(
    prefix = "slices.astrologers.automation",
    name = ["whenweekstartedthenproclaimweeksymbol.enabled"]
)
@Component
private class WhenWeekStartedThenProclaimWeekSymbolProcessor(
    private val weekSymbolCalculator: WeekSymbolCalculator
) {

    @EventHandler
    fun react(
        event: DayStarted,
        @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String,
        @MetadataValue(GameMetadata.PLAYER_ID_KEY) playerId: String,
        commandDispatcher: CommandDispatcher,
    ): CompletableFuture<out Any> {
        if (event.day.raw == FIRST_DAY_OF_THE_WEEK) {
            val weekSymbol = weekSymbolCalculator(MonthWeek(event.month.raw, event.week.raw))
            val command = ProclaimWeekSymbol(
                astrologersId = AstrologersId(event.calendarId.raw),
                week = MonthWeek(event.month.raw, event.week.raw),
                symbol = weekSymbol
            )
            val metadata = GameMetadata.with(GameId(gameId), PlayerId(playerId))
            return commandDispatcher.send(command, metadata).resultMessage
        }
        return CompletableFuture.completedFuture(null)
    }
}
