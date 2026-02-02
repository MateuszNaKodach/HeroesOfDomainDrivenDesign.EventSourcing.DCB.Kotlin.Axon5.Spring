package org.axonframework.test.fixture

import org.axonframework.common.configuration.ApplicationConfigurer
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer

fun configSlice(
    modulesRegistration: EventSourcingConfigurer.() -> EventSourcingConfigurer = { this }
): ApplicationConfigurer {
    val configurer = EventSourcingConfigurer.create()
    return configurer.let(modulesRegistration)
}