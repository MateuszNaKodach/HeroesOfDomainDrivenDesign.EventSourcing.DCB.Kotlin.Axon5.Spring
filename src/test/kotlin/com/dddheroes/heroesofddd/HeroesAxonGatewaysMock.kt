package com.dddheroes.heroesofddd

import com.dddheroes.extensions.axon.test.AxonGatewaysMock
import com.dddheroes.extensions.axon.test.AxonGatewaysMockConfiguration
import com.dddheroes.extensions.axon.test.AxonGatewaysMockSetupListener
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoBeans
import java.time.Clock
import java.time.Instant

/**
 * Heroes-of-DDD–specific decorator around the generic [AxonGatewaysMock].
 *
 * Adds [CommandHandlerResult]-based convenience methods while delegating
 * all generic command, query, and clock stubbing to [AxonGatewaysMock].
 *
 * ## Convenience — [CommandHandlerResult] shortcuts
 *
 * ```kotlin
 * gateways.assumeCommandSuccess<BuildDwelling>()
 * gateways.assumeCommandFailure<BuildDwelling>("Already built")
 *
 * val command = BuildDwelling(dwellingId, creatureId, cost)
 * gateways.assumeCommandSuccess(command)
 * gateways.assumeCommandFailure(command, "Already built")
 * ```
 *
 * ## Generic methods (delegated from [AxonGatewaysMock])
 *
 * ```kotlin
 * gateways.assumeCommandReturns<BuildDwelling>(MyResult("ok"))
 * gateways.assumeCommandException<BuildDwelling>(IllegalStateException("boom"))
 * gateways.assumeQueryReturns(GetDwellingById(id), DwellingView(...))
 * gateways.currentTimeIs(Instant.parse("2024-01-15T10:00:00Z"))
 * ```
 *
 * @see AxonGatewaysMock
 * @see WithHeroesAxonGatewaysMock
 */
class HeroesAxonGatewaysMock(@PublishedApi internal val delegate: AxonGatewaysMock) {

    // ---- Delegated generic methods ----

    /** @see AxonGatewaysMock.assumeCommandReturns */
    fun <C : Any> assumeCommandReturns(command: C, result: Any) =
        delegate.assumeCommandReturns(command, result)

    /** @see AxonGatewaysMock.assumeCommandReturns */
    inline fun <reified C : Any> assumeCommandReturns(result: Any) =
        delegate.assumeCommandReturns<C>(result)

    /** @see AxonGatewaysMock.assumeCommandException */
    fun <C : Any> assumeCommandException(command: C, exception: Exception) =
        delegate.assumeCommandException(command, exception)

    /** @see AxonGatewaysMock.assumeCommandException */
    inline fun <reified C : Any> assumeCommandException(exception: Exception) =
        delegate.assumeCommandException<C>(exception)

    /** @see AxonGatewaysMock.assumeQueryReturns */
    inline fun <reified R, reified Q : Any> assumeQueryReturns(query: Q, result: R) =
        delegate.assumeQueryReturns(query, result)

    /** @see AxonGatewaysMock.currentTimeIs */
    fun currentTimeIs(instant: Instant): Instant = delegate.currentTimeIs(instant)

    /** @see AxonGatewaysMock.currentTime */
    fun currentTime(): Instant = delegate.currentTime()

    // ---- Heroes-specific: Command Success ----

    /** Stubs the CommandGateway to return [CommandHandlerResult.Success] for the given [command] instance. */
    fun <T : Any> assumeCommandSuccess(command: T) {
        delegate.assumeCommandReturns(command, CommandHandlerResult.Success)
    }

    /** Stubs the CommandGateway to return [CommandHandlerResult.Success] for any command of type [T]. */
    inline fun <reified T : Any> assumeCommandSuccess() {
        delegate.assumeCommandReturns<T>(CommandHandlerResult.Success)
    }

    // ---- Heroes-specific: Command Failure ----

    /**
     * Stubs the CommandGateway to return [CommandHandlerResult.Failure] for the given [command] instance.
     *
     * This returns a **successful** future carrying a failure payload.
     * For a **failed** future (exception), use [assumeCommandException].
     */
    fun <T : Any> assumeCommandFailure(command: T, message: String = "Simulated failure") {
        delegate.assumeCommandReturns(command, CommandHandlerResult.Failure(message))
    }

    /**
     * Stubs the CommandGateway to return [CommandHandlerResult.Failure] for any command of type [T].
     *
     * This returns a **successful** future carrying a failure payload.
     * For a **failed** future (exception), use [assumeCommandException].
     */
    inline fun <reified T : Any> assumeCommandFailure(message: String = "Simulated failure") {
        delegate.assumeCommandReturns<T>(CommandHandlerResult.Failure(message))
    }
}

/** Assembles [HeroesAxonGatewaysMock] bean from the generic [AxonGatewaysMock]. */
@TestConfiguration
class HeroesAxonGatewaysMockConfiguration {

    @Bean
    fun heroesAxonGatewaysMock(gateways: AxonGatewaysMock) = HeroesAxonGatewaysMock(gateways)
}

/**
 * Heroes-of-DDD–specific composed annotation for tests requiring mocked Axon gateways.
 *
 * Extends [@WithAxonGatewaysMock][com.dddheroes.extensions.axon.test.WithAxonGatewaysMock] with an
 * additional [HeroesAxonGatewaysMock] bean that provides [CommandHandlerResult]-based
 * convenience methods (`assumeCommandSuccess`, `assumeCommandFailure`).
 *
 * Can be used with any test annotation (`@WebMvcTest`, `@SpringBootTest`, etc.):
 *
 * ```kotlin
 * @RestAssuredMockMvcTest
 * @WithHeroesAxonGatewaysMock
 * @TestPropertySource(properties = ["slices.myslice.enabled=true"])
 * class MyRestApiTest @Autowired constructor(val gateways: HeroesAxonGatewaysMock) {
 *
 *     @Test
 *     fun `command success`() {
 *         gateways.assumeCommandSuccess<MyCommand>()
 *         // RestAssured Given/When/Then ...
 *     }
 * }
 * ```
 *
 * @see HeroesAxonGatewaysMock
 * @see com.dddheroes.extensions.axon.test.AxonGatewaysMock
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MockitoBeans(
    MockitoBean(types = [CommandGateway::class]),
    MockitoBean(types = [QueryGateway::class]),
    MockitoBean(types = [Clock::class])
)
@Import(AxonGatewaysMockConfiguration::class, HeroesAxonGatewaysMockConfiguration::class)
@TestExecutionListeners(
    listeners = [AxonGatewaysMockSetupListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
annotation class WithHeroesAxonGatewaysMock
