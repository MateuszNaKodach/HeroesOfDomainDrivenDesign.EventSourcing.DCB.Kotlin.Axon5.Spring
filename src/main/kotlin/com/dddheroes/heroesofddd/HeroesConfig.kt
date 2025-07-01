package com.dddheroes.heroesofddd

import com.dddheroes.heroesofddd.shared.application.GameMetaData
import org.axonframework.configuration.ConfigurationEnhancer
import org.axonframework.eventsourcing.eventstore.MetaDataBasedTagResolver
import org.axonframework.eventsourcing.eventstore.MultiTagResolver
import org.axonframework.eventsourcing.eventstore.TagResolver
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


}