package org.axonframework.test.fixture

import org.axonframework.common.configuration.ApplicationConfigurer
import org.axonframework.common.configuration.AxonConfiguration

fun springTestFixture(configuration: AxonConfiguration) = AxonTestFixture(
    configuration,
    AxonTestFixture.Customization()
)

fun axonTestFixture(configurer: ApplicationConfigurer) = AxonTestFixture.with(configurer) { customization ->
    if (System.getenv("AXON_AXONSERVER_ENABLED")?.toBoolean() != true) {
        customization.disableAxonServer()
    } else {
        customization
    }
}