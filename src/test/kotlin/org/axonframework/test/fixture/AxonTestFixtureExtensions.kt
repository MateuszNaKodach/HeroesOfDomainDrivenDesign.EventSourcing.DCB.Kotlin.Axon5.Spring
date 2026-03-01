package org.axonframework.test.fixture

import org.axonframework.common.configuration.ApplicationConfigurer
import org.axonframework.common.configuration.AxonConfiguration

fun springTestFixture(
    configuration: AxonConfiguration,
    customization: AxonTestFixture.Customization = AxonTestFixture.Customization()
) = AxonTestFixture(configuration, customization)

fun axonTestFixture(configurer: ApplicationConfigurer): AxonTestFixture =
    AxonTestFixture.with(configurer) { customization ->
    if (System.getenv("AXON_AXONSERVER_ENABLED")?.toBoolean() != true) {
        customization.disableAxonServer()
    } else {
        customization
    }
}