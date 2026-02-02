package com.dddheroes.heroesofddd

import org.axonframework.extension.spring.test.AxonSpringBootTest
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import org.springframework.test.context.ActiveProfiles
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ActiveProfiles("test", "testcontainers", "axonserver")
@Import(TestcontainersConfiguration::class)
// @ActiveProfiles("test", "axonserver") // if you don't want to use testcontainers
@AxonSpringBootTest
annotation class HeroesAxonSpringBootTest(

    @get:AliasFor(annotation = AxonSpringBootTest::class, attribute = "value")
    val value: Array<String> = [],

    @get:AliasFor(annotation = AxonSpringBootTest::class, attribute = "properties")
    val properties: Array<String> = [],

    @get:AliasFor(annotation = AxonSpringBootTest::class, attribute = "args")
    val args: Array<String> = [],

    @get:AliasFor(annotation = AxonSpringBootTest::class, attribute = "classes")
    val classes: Array<KClass<*>> = [],

    @get:AliasFor(annotation = AxonSpringBootTest::class, attribute = "webEnvironment")
    val webEnvironment: WebEnvironment = WebEnvironment.MOCK,

    @get:AliasFor(annotation = AxonSpringBootTest::class, attribute = "useMainMethod")
    val useMainMethod: UseMainMethod = UseMainMethod.NEVER
)
