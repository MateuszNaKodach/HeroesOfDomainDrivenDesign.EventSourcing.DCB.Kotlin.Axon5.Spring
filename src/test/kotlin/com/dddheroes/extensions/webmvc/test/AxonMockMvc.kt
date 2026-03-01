package com.dddheroes.extensions.webmvc.test

import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.commandhandling.gateway.CommandResult
import org.axonframework.messaging.commandhandling.gateway.FutureCommandResult
import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.core.Metadata
import org.axonframework.messaging.eventhandling.GenericEventMessage
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
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
 * Two styles — by command type (reified) or by exact command instance:
 *
 * ```kotlin
 * // Match any command of type BuildDwelling
 * axonMockMvc.assumeCommandSuccess<BuildDwelling>()
 * axonMockMvc.assumeCommandFailure<BuildDwelling>("Already built")
 *
 * // Match a specific command instance (uses eq() matcher)
 * val command = BuildDwelling(dwellingId, creatureId, cost)
 * axonMockMvc.assumeCommandSuccess(command)
 * axonMockMvc.assumeCommandFailure(command, "Already built")
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

    // ---- Command Success ----

    /** Stubs the [CommandGateway] to return [CommandHandlerResult.Success] for the given [command] instance. */
    fun <T : Any> assumeCommandSuccess(command: T) {
        stubCommandGateway(command, CommandHandlerResult.Success)
    }

    /** Stubs the [CommandGateway] to return [CommandHandlerResult.Success] for any command of type [T]. */
    inline fun <reified T : Any> assumeCommandSuccess() {
        stubCommandGatewayByType<T>(CommandHandlerResult.Success)
    }

    // ---- Command Failure ----

    /** Stubs the [CommandGateway] to return [CommandHandlerResult.Failure] for the given [command] instance. */
    fun <T : Any> assumeCommandFailure(command: T, message: String = "Simulated failure") {
        stubCommandGateway(command, CommandHandlerResult.Failure(message))
    }

    /** Stubs the [CommandGateway] to return [CommandHandlerResult.Failure] for any command of type [T]. */
    inline fun <reified T : Any> assumeCommandFailure(message: String = "Simulated failure") {
        stubCommandGatewayByType<T>(CommandHandlerResult.Failure(message))
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
    internal fun <T : Any> stubCommandGateway(command: T, payload: CommandHandlerResult) {
        val result = commandResultOf(payload)
        Mockito.doReturn(result)
            .`when`(commandGateway).send(eq(command), any(Metadata::class.java))
        Mockito.doReturn(result)
            .`when`(commandGateway).send(eq(command))
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(eq(command))
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(eq(command), eq(CommandHandlerResult::class.java))
    }

    /** Stubs all [CommandGateway] invocation styles for any command matching type [T]. */
    @PublishedApi
    internal inline fun <reified T : Any> stubCommandGatewayByType(payload: CommandHandlerResult) {
        val result = commandResultOf(payload)
        Mockito.doReturn(result)
            .`when`(commandGateway).send(argThat { it is T }, any(Metadata::class.java))
        Mockito.doReturn(result)
            .`when`(commandGateway).send(argThat<Any> { it is T })
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(argThat { it is T })
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(argThat { it is T }, eq(CommandHandlerResult::class.java))
    }

    /** Wraps a [CommandHandlerResult] payload in a completed [FutureCommandResult]. */
    @PublishedApi
    internal fun commandResultOf(payload: CommandHandlerResult): CommandResult {
        val message = GenericCommandResultMessage(
            MessageType(CommandHandlerResult::class.java), payload
        )
        return FutureCommandResult(CompletableFuture.completedFuture(message))
    }
}
