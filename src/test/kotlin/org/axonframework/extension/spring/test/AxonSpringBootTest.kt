package org.axonframework.extension.spring.test

import org.axonframework.test.fixture.MessagesRecordingConfigurationEnhancer
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
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
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
