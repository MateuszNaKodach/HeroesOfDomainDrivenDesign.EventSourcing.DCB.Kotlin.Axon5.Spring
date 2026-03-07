package com.dddheroes.extensions.webmvc.test

import com.dddheroes.heroesofddd.SecurityConfiguration
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.springframework.beans.factory.getBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.web.servlet.MockMvc
import kotlin.reflect.KClass

/**
 * [TestExecutionListener] that configures [RestAssuredMockMvc] with the current [MockMvc]
 * instance before each test method.
 *
 * Registered automatically by [@RestAssuredMockMvcTest][RestAssuredMockMvcTest].
 */
class RestAssuredMockMvcSetupListener : TestExecutionListener {

    override fun beforeTestMethod(testContext: TestContext) {
        val mockMvc = testContext.applicationContext.getBean<MockMvc>()
        RestAssuredMockMvc.mockMvc(mockMvc)
    }
}

/**
 * Composed annotation for REST API controller tests using RestAssured MockMvc.
 *
 * Combines [@WebMvcTest][WebMvcTest] with automatic [RestAssuredMockMvc] setup
 * via [RestAssuredMockMvcSetupListener] — no `@BeforeEach` boilerplate needed.
 *
 * Compose with gateway mocking annotations (e.g. `@AxonGatewaysMock`) as needed:
 *
 * ```kotlin
 * @RestAssuredMockMvcTest
 * @AxonGatewaysMock
 * @TestPropertySource(properties = ["slices.myslice.enabled=true"])
 * class MyRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {
 *
 *     @Test
 *     fun `returns 200`() {
 *         gateways.assumeCommandReturns<MyCommand>(MyResult("ok"))
 *         Given { ... } When { get("/endpoint") } Then { statusCode(200) }
 *     }
 * }
 * ```
 *
 * All [@WebMvcTest][WebMvcTest] attributes (`controllers`, `properties`, etc.) are forwarded.
 *
 * @see WebMvcTest
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@WebMvcTest
@Import(SecurityConfiguration::class) // project specific
@TestExecutionListeners(
    listeners = [RestAssuredMockMvcSetupListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
annotation class RestAssuredMockMvcTest(

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
