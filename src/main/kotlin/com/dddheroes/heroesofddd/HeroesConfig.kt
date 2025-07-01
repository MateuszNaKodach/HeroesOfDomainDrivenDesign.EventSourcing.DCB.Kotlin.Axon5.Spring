package com.dddheroes.heroesofddd

import com.dddheroes.heroesofddd.shared.application.GameMetaData
import org.axonframework.configuration.ConfigurationEnhancer
import org.axonframework.eventsourcing.eventstore.MetaDataBasedTagResolver
import org.axonframework.eventsourcing.eventstore.MultiTagResolver
import org.axonframework.eventsourcing.eventstore.TagResolver
import org.axonframework.messaging.correlation.CorrelationDataProvider
import org.axonframework.messaging.correlation.MessageOriginProvider
import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider
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
                MetaDataBasedTagResolver(GameMetaData.GAME_ID_KEY),
                MetaDataBasedTagResolver(GameMetaData.PLAYER_ID_KEY),
                d
            )
        }
    }

    @Bean
    fun gameDataProvider(): CorrelationDataProvider {
        return SimpleCorrelationDataProvider(GameMetaData.GAME_ID_KEY, GameMetaData.PLAYER_ID_KEY)
    }

    @Bean
    fun messageOriginProvider(): CorrelationDataProvider {
        return MessageOriginProvider()
    }


}