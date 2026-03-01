package com.dddheroes.heroesofddd

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
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture

abstract class RestApiSpringBootTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var commandGateway: CommandGateway

    @MockitoBean
    lateinit var queryGateway: QueryGateway

    @MockitoBean
    lateinit var clock: Clock

    @BeforeEach
    fun setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc)
        currentTimeIs(Instant.now())
    }

    // ---- Command Success ----

    protected fun <T : Any> assumeCommandSuccess(command: T) {
        stubCommandGateway(command, CommandHandlerResult.Success)
    }

    final inline fun <reified T : Any> assumeCommandSuccess() {
        stubCommandGatewayByType<T>(CommandHandlerResult.Success)
    }

    // ---- Command Failure ----

    protected fun <T : Any> assumeCommandFailure(command: T, message: String = "Simulated failure") {
        stubCommandGateway(command, CommandHandlerResult.Failure(message))
    }

    final inline fun <reified T : Any> assumeCommandFailure(message: String = "Simulated failure") {
        stubCommandGatewayByType<T>(CommandHandlerResult.Failure(message))
    }

    // ---- Query ----

    protected inline fun <reified R, reified Q : Any> assumeQueryReturns(query: Q, result: R) {
        Mockito.doReturn(CompletableFuture.completedFuture(result))
            .`when`(queryGateway).query(eq(query), eq(R::class.java))
    }

    // ---- Clock ----

    protected fun currentTimeIs(instant: Instant): Instant {
        Mockito.`when`(clock.instant()).thenReturn(instant)
        GenericEventMessage.clock = Clock.fixed(instant, ZoneOffset.UTC)
        return instant
    }

    protected fun currentTime(): Instant = clock.instant()

    // ---- Internals ----

    /**
     * AF5 [CommandGateway] has these invocation styles:
     * - `send(command, metadata)` → [CommandResult] (async with metadata — most common in controllers)
     * - `send(command)` → [CommandResult] (async without metadata)
     * - `sendAndWait(command)` → `Any?` (sync)
     * - `sendAndWait(command, resultType)` → `R?` (sync typed)
     *
     * All are stubbed so tests work regardless of which style the controller uses.
     */
    private fun <T : Any> stubCommandGateway(command: T, payload: CommandHandlerResult) {
        val result = commandResultOf(payload)
        // send(command, metadata) — async with metadata
        Mockito.doReturn(result)
            .`when`(commandGateway).send(eq(command), any(Metadata::class.java))
        // send(command) — async without metadata
        Mockito.doReturn(result)
            .`when`(commandGateway).send(eq(command))
        // sendAndWait(command) — sync
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(eq(command))
        // sendAndWait(command, resultType) — sync typed
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(eq(command), eq(CommandHandlerResult::class.java))
    }

    @PublishedApi
    internal inline fun <reified T : Any> stubCommandGatewayByType(payload: CommandHandlerResult) {
        val result = commandResultOf(payload)
        // send(command, metadata) — async with metadata
        Mockito.doReturn(result)
            .`when`(commandGateway).send(argThat { it is T }, any(Metadata::class.java))
        // send(command) — async without metadata
        Mockito.doReturn(result)
            .`when`(commandGateway).send(argThat<Any> { it is T })
        // sendAndWait(command) — sync
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(argThat { it is T })
        // sendAndWait(command, resultType) — sync typed
        Mockito.doReturn(payload)
            .`when`(commandGateway).sendAndWait(argThat { it is T }, eq(CommandHandlerResult::class.java))
    }

    @PublishedApi
    internal fun commandResultOf(payload: CommandHandlerResult): CommandResult {
        val message = GenericCommandResultMessage(
            MessageType(CommandHandlerResult::class.java), payload
        )
        return FutureCommandResult(CompletableFuture.completedFuture(message))
    }
}
