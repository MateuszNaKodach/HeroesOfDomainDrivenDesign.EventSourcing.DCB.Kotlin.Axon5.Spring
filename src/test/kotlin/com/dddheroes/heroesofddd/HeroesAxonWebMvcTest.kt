package com.dddheroes.heroesofddd

import com.dddheroes.extensions.webmvc.test.AxonMockMvc
import com.dddheroes.extensions.webmvc.test.AxonWebMvcTest
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import kotlin.reflect.KClass

/**
 * Spring [TestConfiguration] that assembles [HeroesAxonMockMvc] from the generic [AxonMockMvc]
 * bean provided by [@AxonWebMvcTest][AxonWebMvcTest].
 *
 * Imported automatically by [@HeroesAxonWebMvcTest][HeroesAxonWebMvcTest].
 */
@TestConfiguration
class HeroesAxonMockMvcConfiguration {

    @Bean
    fun heroesAxonMockMvc(axonMockMvc: AxonMockMvc) = HeroesAxonMockMvc(axonMockMvc)
}

/**
 * Heroes-of-DDD–specific composed annotation for REST API controller tests.
 *
 * Extends [@AxonWebMvcTest][AxonWebMvcTest] with an additional [HeroesAxonMockMvc] bean
 * that provides `CommandHandlerResult`-based convenience methods (`assumeCommandSuccess`,
 * `assumeCommandFailure`) on top of the generic [AxonMockMvc] infrastructure.
 *
 * Usage:
 * ```kotlin
 * @HeroesAxonWebMvcTest
 * @TestPropertySource(properties = ["slices.myslice.enabled=true"])
 * class MyRestApiTest @Autowired constructor(val axonMockMvc: HeroesAxonMockMvc) {
 *
 *     @Test
 *     fun `command success`() {
 *         axonMockMvc.assumeCommandSuccess<MyCommand>()
 *         // RestAssured Given/When/Then ...
 *     }
 * }
 * ```
 *
 * All [@WebMvcTest][WebMvcTest] attributes (`controllers`, `properties`, etc.) are forwarded.
 *
 * @see HeroesAxonMockMvc
 * @see AxonWebMvcTest
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@AxonWebMvcTest
@Import(HeroesAxonMockMvcConfiguration::class)
annotation class HeroesAxonWebMvcTest(

    @get:AliasFor(annotation = WebMvcTest::class, attribute = "value")
    val value: Array<KClass<*>> = [],

    @get:AliasFor(annotation = WebMvcTest::class, attribute = "controllers")
    val controllers: Array<KClass<*>> = [],

    @get:AliasFor(annotation = WebMvcTest::class, attribute = "properties")
    val properties: Array<String> = [],

    @get:AliasFor(annotation = WebMvcTest::class, attribute = "useDefaultFilters")
    val useDefaultFilters: Boolean = true,

    @get:AliasFor(annotation = WebMvcTest::class, attribute = "excludeAutoConfiguration")
    val excludeAutoConfiguration: Array<KClass<*>> = []
)
