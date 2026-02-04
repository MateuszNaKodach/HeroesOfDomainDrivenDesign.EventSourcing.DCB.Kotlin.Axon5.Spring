package com.dddheroes.heroesofddd

import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.DomainRuleViolatedException
import com.dddheroes.heroesofddd.shared.restapi.ErrorResponse
import org.axonframework.common.configuration.ConfigurationEnhancer
import org.axonframework.eventsourcing.eventstore.MetadataBasedTagResolver
import org.axonframework.eventsourcing.eventstore.MultiTagResolver
import org.axonframework.eventsourcing.eventstore.TagResolver
import org.axonframework.messaging.core.correlation.CorrelationDataProvider
import org.axonframework.messaging.core.correlation.MessageOriginProvider
import org.axonframework.messaging.core.correlation.SimpleCorrelationDataProvider
import org.axonframework.messaging.eventhandling.GenericEventMessage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Clock


@Configuration
internal class HeroesConfig {

    @Bean
    fun tagResolverEnhancer(): ConfigurationEnhancer = ConfigurationEnhancer { configuration ->
        configuration.registerDecorator(
            TagResolver::class.java,
            Integer.MAX_VALUE
        ) { c, n, d ->
            MultiTagResolver(
                MetadataBasedTagResolver(GameMetadata.GAME_ID_KEY),
                MetadataBasedTagResolver(GameMetadata.PLAYER_ID_KEY),
                d
            )
        }
    }

    @Bean
    fun gameDataProvider(): CorrelationDataProvider {
        return SimpleCorrelationDataProvider(GameMetadata.GAME_ID_KEY, GameMetadata.PLAYER_ID_KEY)
    }

    @Bean
    fun messageOriginProvider(): CorrelationDataProvider {
        return MessageOriginProvider()
    }

    @Bean
    fun clock(): Clock {
        val clock = Clock.systemUTC()
        GenericEventMessage.clock = clock
        return clock
    }

}


@RestControllerAdvice
internal class GlobalControllerExceptionHandler {

    @ExceptionHandler(DomainRuleViolatedException::class)
    fun handleDomainRuleViolatedException(e: DomainRuleViolatedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest()
            .body(ErrorResponse(e.message ?: "Unknown error occurred"))
    }

}