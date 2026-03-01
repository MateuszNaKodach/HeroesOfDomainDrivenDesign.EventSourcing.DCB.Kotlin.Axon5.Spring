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
import org.mockito.ArgumentMatchers.any
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

    protected fun assumeCommandSuccess() {
        val result = commandResultOf(CommandHandlerResult.Success)
        Mockito.doReturn(result)
            .`when`(commandGateway).send(any(), any(Metadata::class.java))
    }

    protected fun assumeCommandFailure(message: String) {
        val result = commandResultOf(CommandHandlerResult.Failure(message))
        Mockito.doReturn(result)
            .`when`(commandGateway).send(any(), any(Metadata::class.java))
    }

    protected fun currentTimeIs(instant: Instant): Instant {
        Mockito.`when`(clock.instant()).thenReturn(instant)
        GenericEventMessage.clock = Clock.fixed(instant, ZoneOffset.UTC)
        return instant
    }

    protected fun currentTime(): Instant = clock.instant()

    private fun commandResultOf(payload: CommandHandlerResult): CommandResult {
        val message = GenericCommandResultMessage(
            MessageType(CommandHandlerResult::class.java), payload
        )
        return FutureCommandResult(CompletableFuture.completedFuture(message))
    }
}
