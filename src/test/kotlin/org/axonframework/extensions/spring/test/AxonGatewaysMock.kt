package org.axonframework.extensions.spring.test

import org.axonframework.messaging.commandhandling.GenericCommandResultMessage
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.commandhandling.gateway.CommandResult
import org.axonframework.messaging.commandhandling.gateway.FutureCommandResult
import org.axonframework.messaging.core.MessageTypeResolver
import org.axonframework.messaging.core.Metadata
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver
import org.axonframework.messaging.eventhandling.GenericEventMessage
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoBeans
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture

/**
 * Generic test helper for stubbing Axon Framework 5 [CommandGateway], [QueryGateway], and [Clock].
 *
 * Independent of MockMvc — usable with any test type (`@WebMvcTest`, `@SpringBootTest`, etc.).
 * Injected as a Spring bean via the [@AxonGatewaysMock][AxonGatewaysMock] annotation.
 *
 * ## Command stubbing
 *
 * ### Result — any payload type
 *
 * ```kotlin
 * gateways.assumeCommandReturns<BuildDwelling>(MyResult("ok"))
 * gateways.assumeCommandReturns(specificCommand, MyResult("ok"))
 * ```
 *
 * ### Exception — failed future / thrown exception
 *
 * ```kotlin
 * gateways.assumeCommandException<BuildDwelling>(IllegalStateException("boom"))
 * gateways.assumeCommandException(specificCommand, IllegalStateException("boom"))
 * ```
 *
 * Each stub covers all AF5 [CommandGateway] invocation styles:
 * `send(command, metadata)`, `send(command)`, `sendAndWait(command)`,
 * and `sendAndWait(command, resultType)`.
 *
 * ## Query stubbing
 *
 * ```kotlin
 * gateways.assumeQueryReturns(GetDwellingById(id), DwellingView(...))
 * ```
 *
 * ## Clock control
 *
 * ```kotlin
 * val now = gateways.currentTimeIs(Instant.parse("2024-01-15T10:00:00Z"))
 * val current = gateways.currentTime()
 * ```
 */
class AxonGatewaysMock(
    @PublishedApi internal val commandGateway: CommandGateway,
    @PublishedApi internal val queryGateway: QueryGateway,
    private val clock: Clock,
    private val messageTypeResolver: MessageTypeResolver
) {

    /**
     * Resets the [Clock] mock to `Instant.now()`.
     *
     * Called automatically before each test by [AxonGatewaysMockSetupListener].
     */
    fun resetClock() {
        currentTimeIs(Instant.now())
    }

    // ---- Generic Command Stubbing ----

    /** Stubs the [CommandGateway] to return [result] for the given [command] instance. Works with any result type. */
    fun <C : Any> assumeCommandReturns(command: C, result: Any) {
        stubCommandGateway(command, result)
    }

    /** Stubs the [CommandGateway] to return [result] for any command of type [C]. Works with any result type. */
    inline fun <reified C : Any> assumeCommandReturns(result: Any) {
        stubCommandGatewayByType<C>(result)
    }

    // ---- Command Exception ----

    /**
     * Stubs the [CommandGateway] to fail with [exception] for the given [command] instance.
     *
     * Makes `send()` return a failed [CompletableFuture] and `sendAndWait()` throw the exception.
     */
    fun <C : Any> assumeCommandException(command: C, exception: Exception) {
        stubCommandGatewayException(command, exception)
    }

    /**
     * Stubs the [CommandGateway] to fail with [exception] for any command of type [C].
     *
     * Makes `send()` return a failed [CompletableFuture] and `sendAndWait()` throw the exception.
     */
    inline fun <reified C : Any> assumeCommandException(exception: Exception) {
        stubCommandGatewayExceptionByType<C>(exception)
    }

    // ---- Query ----

    /** Stubs the [QueryGateway] to return [result] when the given [query] is dispatched. */
    inline fun <reified R : Any, reified Q : Any> assumeQueryReturns(query: Q, result: R) {
        Mockito.doReturn(CompletableFuture.completedFuture(result))
            .`when`(queryGateway).query(eq(query), eq(R::class.java))
    }

    /** Stubs the [QueryGateway] to return `null` when the given [query] is dispatched. */
    @Suppress("UNCHECKED_CAST")
    fun <Q : Any> assumeQueryReturnsNull(query: Q) {
        Mockito.doReturn(CompletableFuture.completedFuture(null))
            .`when`(queryGateway).query(eq(query), any(Class::class.java) as Class<Any>)
    }

    // ---- Clock ----

    /**
     * Fixes the [Clock] mock and [GenericEventMessage.clock] to the given [instant].
     *
     * @return the same [instant] for convenient assignment.
     */
    fun currentTimeIs(instant: Instant): Instant {
        Mockito.`when`(clock.instant()).thenReturn(instant)
        @Suppress("DEPRECATION")
        GenericEventMessage.clock = Clock.fixed(instant, ZoneOffset.UTC)
        return instant
    }

    /** Returns the current time from the mocked [Clock]. */
    fun currentTime(): Instant = clock.instant()

    // ---- Internals ----

    /**
     * Stubs all [CommandGateway] invocation styles for a specific command instance.
     *
     * AF5 [CommandGateway] dispatch methods:
     * - `send(command, metadata)` -> [CommandResult] (async with metadata)
     * - `send(command)` -> [CommandResult] (async without metadata)
     * - `sendAndWait(command)` -> `Any?` (sync)
     * - `sendAndWait(command, resultType)` -> `R?` (sync typed)
     */
    @PublishedApi
    internal fun <C : Any> stubCommandGateway(command: C, payload: Any) {
        val result = commandResultOf(payload)
        Mockito.doReturn(result)
            .`when`(commandGateway).send(eq(command), any(Metadata::class.java))
        Mockito.doReturn(result)
            .`when`(commandGateway).send(eq(command))
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(eq(command))
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(eq(command), any(Class::class.java))
    }

    /** Stubs all [CommandGateway] invocation styles for any command matching type [T]. */
    @PublishedApi
    internal inline fun <reified T : Any> stubCommandGatewayByType(payload: Any) {
        val result = commandResultOf(payload)
        Mockito.doReturn(result)
            .`when`(commandGateway).send(argThat { it is T }, any(Metadata::class.java))
        Mockito.doReturn(result)
            .`when`(commandGateway).send(argThat<Any> { it is T })
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(argThat { it is T })
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(argThat { it is T }, any(Class::class.java))
    }

    /** Stubs all [CommandGateway] invocation styles to fail with [exception] for a specific command instance. */
    @PublishedApi
    internal fun <C : Any> stubCommandGatewayException(command: C, exception: Exception) {
        val failedResult = failedCommandResult(exception)
        Mockito.doReturn(failedResult)
            .`when`(commandGateway).send(eq(command), any(Metadata::class.java))
        Mockito.doReturn(failedResult)
            .`when`(commandGateway).send(eq(command))
        Mockito.doThrow(exception)
            .`when`(commandGateway).sendAndWait(eq(command))
        Mockito.doThrow(exception)
            .`when`(commandGateway).sendAndWait(eq(command), any(Class::class.java))
    }

    /** Stubs all [CommandGateway] invocation styles to fail with [exception] for any command matching type [T]. */
    @PublishedApi
    internal inline fun <reified T : Any> stubCommandGatewayExceptionByType(exception: Exception) {
        val failedResult = failedCommandResult(exception)
        Mockito.doReturn(failedResult)
            .`when`(commandGateway).send(argThat { it is T }, any(Metadata::class.java))
        Mockito.doReturn(failedResult)
            .`when`(commandGateway).send(argThat<Any> { it is T })
        Mockito.doThrow(exception)
            .`when`(commandGateway).sendAndWait(argThat { it is T })
        Mockito.doThrow(exception)
            .`when`(commandGateway).sendAndWait(argThat { it is T }, any(Class::class.java))
    }

    /** Creates a [FutureCommandResult] wrapping a failed [CompletableFuture]. */
    @PublishedApi
    internal fun failedCommandResult(exception: Exception): CommandResult =
        FutureCommandResult(CompletableFuture.failedFuture(exception))

    /** Wraps a payload in a completed [FutureCommandResult]. Works with any result type. */
    @PublishedApi
    internal fun commandResultOf(payload: Any): CommandResult {
        val messageType = messageTypeResolver.resolveOrThrow(payload)
        val message = GenericCommandResultMessage(messageType, payload)
        return FutureCommandResult(CompletableFuture.completedFuture(message))
    }
}

/** Assembles [AxonGatewaysMock] bean from the mocked gateways and [Clock]. */
@TestConfiguration
class AxonGatewaysMockConfiguration {

    // fixme: AF5 - should be bean in Axon by default
    @Bean
    fun messageTypeResolver(): MessageTypeResolver = AnnotationMessageTypeResolver()

    @Bean
    @ConditionalOnMissingBean(Clock::class)
    @Suppress("DEPRECATION")
    fun clock(): Clock {
        val clock = Clock.systemUTC()
        GenericEventMessage.clock = clock
        return clock
    }

    @Bean
    fun axonGatewaysMock(
        commandGateway: CommandGateway,
        queryGateway: QueryGateway,
        clock: Clock,
        messageTypeResolver: MessageTypeResolver
    ) = AxonGatewaysMock(commandGateway, queryGateway, clock, messageTypeResolver)
}

/** Resets the [Clock] mock before each test (after Spring's mock reset). */
class AxonGatewaysMockSetupListener : TestExecutionListener {

    override fun beforeTestMethod(testContext: TestContext) {
        testContext.applicationContext.getBean<AxonGatewaysMock>().resetClock()
    }
}

/**
 * Composable annotation that provides mocked [CommandGateway], [QueryGateway], and [Clock],
 * plus an [AxonGatewaysMock] bean for stubbing them.
 *
 * Can be used with any test annotation (`@WebMvcTest`, `@SpringBootTest`, etc.):
 *
 * ```kotlin
 * @AxonGatewaysMockTest
 * @SpringBootTest
 * class MyTest @Autowired constructor(val gateways: AxonGatewaysMock) { ... }
 * ```
 *
 * @see AxonGatewaysMock
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MockitoBeans(
    MockitoBean(types = [CommandGateway::class]),
    MockitoBean(types = [QueryGateway::class]),
    MockitoBean(types = [Clock::class])
)
@Import(AxonGatewaysMockConfiguration::class)
@TestExecutionListeners(
    listeners = [AxonGatewaysMockSetupListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
annotation class AxonGatewaysMockTest
