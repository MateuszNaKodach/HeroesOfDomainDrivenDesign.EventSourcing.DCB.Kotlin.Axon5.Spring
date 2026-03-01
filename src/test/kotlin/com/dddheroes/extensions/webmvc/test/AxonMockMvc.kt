package com.dddheroes.extensions.webmvc.test

import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.commandhandling.gateway.CommandResult
import org.axonframework.messaging.commandhandling.gateway.FutureCommandResult
import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.core.Metadata
import org.axonframework.messaging.eventhandling.GenericEventMessage
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito
import org.springframework.test.web.servlet.MockMvc
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture

/**
 * Test helper for Axon Framework 5 REST API tests.
 *
 * Provides methods to stub [CommandGateway], [QueryGateway], and [Clock] mocks
 * without requiring test class inheritance. Injected as a Spring bean via
 * [@AxonWebMvcTest][AxonWebMvcTest].
 *
 * ## Command stubbing
 *
 * ### Result — any payload type
 *
 * ```kotlin
 * axonMockMvc.assumeCommandReturns<BuildDwelling>(MyResult("ok"))
 * axonMockMvc.assumeCommandReturns(specificCommand, MyResult("ok"))
 * ```
 *
 * ### Exception — failed future / thrown exception
 *
 * ```kotlin
 * axonMockMvc.assumeCommandException<BuildDwelling>(IllegalStateException("boom"))
 * axonMockMvc.assumeCommandException(specificCommand, IllegalStateException("boom"))
 * ```
 *
 * Each stub covers all AF5 [CommandGateway] invocation styles:
 * `send(command, metadata)`, `send(command)`, `sendAndWait(command)`,
 * and `sendAndWait(command, resultType)`.
 *
 * ## Query stubbing
 *
 * ```kotlin
 * axonMockMvc.assumeQueryReturns(GetDwellingById(id), DwellingView(...))
 * ```
 *
 * ## Clock control
 *
 * ```kotlin
 * val now = axonMockMvc.currentTimeIs(Instant.parse("2024-01-15T10:00:00Z"))
 * val current = axonMockMvc.currentTime()
 * ```
 *
 * @see AxonWebMvcTest
 */
class AxonMockMvc(
    private val mockMvc: MockMvc,
    @PublishedApi internal val commandGateway: CommandGateway,
    @PublishedApi internal val queryGateway: QueryGateway,
    private val clock: Clock
) {

    /**
     * Configures [RestAssuredMockMvc] with the current [MockMvc] and sets [Clock] to `Instant.now()`.
     *
     * Called automatically before each test by [AxonMockMvcSetupListener].
     */
    fun setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc)
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
    inline fun <reified R, reified Q : Any> assumeQueryReturns(query: Q, result: R) {
        Mockito.doReturn(CompletableFuture.completedFuture(result))
            .`when`(queryGateway).query(eq(query), eq(R::class.java))
    }

    // ---- Clock ----

    /**
     * Fixes the [Clock] mock and [GenericEventMessage.clock] to the given [instant].
     *
     * @return the same [instant] for convenient assignment.
     */
    fun currentTimeIs(instant: Instant): Instant {
        Mockito.`when`(clock.instant()).thenReturn(instant)
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
        val message = GenericCommandResultMessage(
            MessageType(payload::class.java), payload
        )
        return FutureCommandResult(CompletableFuture.completedFuture(message))
    }
}
