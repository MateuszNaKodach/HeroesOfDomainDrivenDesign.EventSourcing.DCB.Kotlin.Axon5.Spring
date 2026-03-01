package com.dddheroes.heroesofddd

import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.commandhandling.gateway.CommandResult
import org.axonframework.messaging.commandhandling.gateway.FutureCommandResult
import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.core.Metadata
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.CompletableFuture

abstract class RestApiSpringBootTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @MockitoBean
    protected lateinit var commandGateway: CommandGateway

    @MockitoBean
    protected lateinit var queryGateway: QueryGateway

    @BeforeEach
    fun setUpRestAssured() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        RestAssuredMockMvc.mockMvc(mockMvc)
    }

    protected fun assumeCommandSuccess() {
        val result = commandResultOf(CommandHandlerResult.Success)
        `when`(commandGateway.send(any(), any(Metadata::class.java))).thenReturn(result)
    }

    protected fun assumeCommandFailure(message: String) {
        val result = commandResultOf(CommandHandlerResult.Failure(message))
        `when`(commandGateway.send(any(), any(Metadata::class.java))).thenReturn(result)
    }

    private fun commandResultOf(payload: CommandHandlerResult): CommandResult {
        val message = GenericCommandResultMessage(
            MessageType(CommandHandlerResult::class.java), payload
        )
        return FutureCommandResult(CompletableFuture.completedFuture(message))
    }
}
