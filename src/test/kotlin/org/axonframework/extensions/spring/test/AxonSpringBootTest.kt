package org.axonframework.extensions.spring.test

import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.MessagesRecordingConfigurationEnhancer
import org.axonframework.test.fixture.springTestFixture
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import kotlin.reflect.KClass

@TestConfiguration
class AxonTestConfiguration {

    @Bean
    fun recordingEnhancer() = MessagesRecordingConfigurationEnhancer()

    @Bean
    fun axonTestFixture(
        configuration: AxonConfiguration,
        customization: ObjectProvider<AxonTestFixture.Customization>
    ): AxonTestFixture = springTestFixture(
        configuration,
        customization.getIfAvailable { AxonTestFixture.Customization() }
    )
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("integration")
@SpringBootTest
@Import(AxonTestConfiguration::class)
annotation class AxonSpringBootTest(

    @get:AliasFor(annotation = SpringBootTest::class, attribute = "value")
    val value: Array<String> = [],

    @get:AliasFor(annotation = SpringBootTest::class, attribute = "properties")
    val properties: Array<String> = [],

    @get:AliasFor(annotation = SpringBootTest::class, attribute = "args")
    val args: Array<String> = [],

    @get:AliasFor(annotation = SpringBootTest::class, attribute = "classes")
    val classes: Array<KClass<*>> = [],

    @get:AliasFor(annotation = SpringBootTest::class, attribute = "webEnvironment")
    val webEnvironment: WebEnvironment = WebEnvironment.MOCK,

    @get:AliasFor(annotation = SpringBootTest::class, attribute = "useMainMethod")
    val useMainMethod: UseMainMethod = UseMainMethod.NEVER
)
