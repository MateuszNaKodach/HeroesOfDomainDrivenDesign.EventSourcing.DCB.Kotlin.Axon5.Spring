package com.dddheroes.heroesofddd

import com.dddheroes.heroesofddd.shared.application.GameMetadata
import org.axonframework.common.configuration.ConfigurationEnhancer
import org.axonframework.eventsourcing.eventstore.MetadataBasedTagResolver
import org.axonframework.eventsourcing.eventstore.MultiTagResolver
import org.axonframework.eventsourcing.eventstore.TagResolver
import org.axonframework.messaging.core.correlation.CorrelationDataProvider
import org.axonframework.messaging.core.correlation.MessageOriginProvider
import org.axonframework.messaging.core.correlation.SimpleCorrelationDataProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


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


}