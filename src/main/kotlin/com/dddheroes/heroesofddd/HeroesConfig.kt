package com.dddheroes.heroesofddd

import org.axonframework.configuration.ConfigurationEnhancer
import org.axonframework.eventsourcing.eventstore.TagResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HeroesConfig {

    @Bean
    fun metaDataTagResolverEnhancer(): ConfigurationEnhancer = ConfigurationEnhancer { configuration ->
        configuration.registerDecorator(
            TagResolver::class.java,
            Integer.MAX_VALUE
        ) { c, n, d -> d }
    }


}