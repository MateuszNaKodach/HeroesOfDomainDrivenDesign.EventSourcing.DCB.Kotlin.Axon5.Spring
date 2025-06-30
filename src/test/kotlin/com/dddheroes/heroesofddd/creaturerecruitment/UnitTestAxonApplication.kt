package com.dddheroes.heroesofddd.creaturerecruitment

import org.axonframework.axonserver.connector.AxonServerConfiguration
import org.axonframework.axonserver.connector.ServerConnectorConfigurationEnhancer
import org.axonframework.configuration.ApplicationConfigurer
import org.axonframework.configuration.ComponentRegistry
import org.axonframework.configuration.Configuration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer

class UnitTestAxonApplication {

    data class ConfigurationProperties(var axonServerEnabled: Boolean = true)

    companion object {
        fun configurer(
            modulesRegistration: EventSourcingConfigurer.() -> EventSourcingConfigurer = { this },
            configOverride: ConfigurationProperties.() -> Unit = {}
        ): ApplicationConfigurer {
            val configProps = ConfigurationProperties().apply(configOverride)
            val configurer = EventSourcingConfigurer.create()
            if (configProps.axonServerEnabled) {
                configurer.componentRegistry { r: ComponentRegistry ->
                    r.registerComponent(
                        AxonServerConfiguration::class.java
                    ) { c: Configuration ->
                        val axonServerConfig = AxonServerConfiguration()
                        axonServerConfig.context = "heroesofddd"
                        axonServerConfig
                    }
                }
            } else {
                configurer.componentRegistry { r: ComponentRegistry ->
                    r.disableEnhancer(
                        ServerConnectorConfigurationEnhancer::class.java
                    )
                }
            }
            return configurer.let(modulesRegistration)
        }
    }
}