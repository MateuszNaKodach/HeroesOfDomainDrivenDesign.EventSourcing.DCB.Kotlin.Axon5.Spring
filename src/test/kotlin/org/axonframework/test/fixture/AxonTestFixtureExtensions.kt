package org.axonframework.test.fixture

import org.axonframework.common.configuration.ApplicationConfigurer

fun axonTestFixture(configurer: ApplicationConfigurer): AxonTestFixture =
    AxonTestFixture.with(configurer) { customization ->
    if (System.getenv("AXON_AXONSERVER_ENABLED")?.toBoolean() == true) {
        customization.asIntegrationTest()
    } else {
        customization
    }
}