package org.axonframework.test.fixture

import org.axonframework.common.configuration.AxonConfiguration

fun springTestFixture(configuration: AxonConfiguration) = AxonTestFixture(
    configuration,
    AxonTestFixture.Customization()
)