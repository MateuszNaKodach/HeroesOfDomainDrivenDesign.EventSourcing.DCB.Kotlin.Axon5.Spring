package com.dddheroes.extensions.webmvc.test

import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoBeans
import org.springframework.test.web.servlet.MockMvc
import java.time.Clock
import kotlin.reflect.KClass

/**
 * Spring [TestConfiguration] that assembles [AxonMockMvc] from the mocked Axon gateways
 * and [MockMvc] provided by [WebMvcTest].
 *
 * Imported automatically by [@AxonWebMvcTest][AxonWebMvcTest].
 */
@TestConfiguration
class AxonMockMvcConfiguration {

    @Bean
    fun axonMockMvc(
        mockMvc: MockMvc,
        commandGateway: CommandGateway,
        queryGateway: QueryGateway,
        clock: Clock
    ) = AxonMockMvc(mockMvc, commandGateway, queryGateway, clock)
}

/**
 * [TestExecutionListener] that calls [AxonMockMvc.setUp] before each test method.
 *
 * Configures [RestAssuredMockMvc][io.restassured.module.mockmvc.RestAssuredMockMvc] with the
 * current [MockMvc] instance and resets the [Clock] to `Instant.now()`.
 * Mock reset itself is handled by Spring's built-in `MockitoResetTestExecutionListener`.
 */
class AxonMockMvcSetupListener : TestExecutionListener {

    override fun beforeTestMethod(testContext: TestContext) {
        testContext.applicationContext.getBean<AxonMockMvc>().setUp()
    }
}

/**
 * Composed annotation for Axon Framework 5 REST API controller tests.
 *
 * Combines [@WebMvcTest][WebMvcTest] with:
 * - Mockito mocks for [CommandGateway], [QueryGateway], and [Clock]
 *   (auto-reset after each test by Spring)
 * - An [AxonMockMvc] bean providing helper methods for stubbing commands, queries, and time
 * - RestAssured MockMvc setup via [AxonMockMvcSetupListener]
 *
 * Usage:
 * ```kotlin
 * @AxonWebMvcTest
 * @TestPropertySource(properties = ["slices.myslice.enabled=true"])
 * class MyRestApiTest {
 *
 *     @Autowired
 *     lateinit var axonMockMvc: AxonMockMvc
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
 * @see AxonMockMvc
 * @see WebMvcTest
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MockitoBeans(
    MockitoBean(types = [CommandGateway::class]),
    MockitoBean(types = [QueryGateway::class]),
    MockitoBean(types = [Clock::class])
)
@WebMvcTest
@Import(AxonMockMvcConfiguration::class)
@TestExecutionListeners(
    listeners = [AxonMockMvcSetupListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
annotation class AxonWebMvcTest(

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
