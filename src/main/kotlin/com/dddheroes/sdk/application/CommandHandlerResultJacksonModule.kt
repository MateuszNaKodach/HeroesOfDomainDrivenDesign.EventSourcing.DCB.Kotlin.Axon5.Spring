package com.dddheroes.sdk.application

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.stereotype.Component
import tools.jackson.databind.module.SimpleModule

@Component
class CommandHandlerResultJacksonModule : SimpleModule() {

    init {
        setMixInAnnotation(CommandHandlerResult::class.java, CommandHandlerResultMixin::class.java)
        setMixInAnnotation(CommandHandlerResult.Success::class.java, SuccessMixin::class.java)
        setMixInAnnotation(CommandHandlerResult.Failure::class.java, FailureMixin::class.java)
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = CommandHandlerResult.Success::class, name = "Success"),
        JsonSubTypes.Type(value = CommandHandlerResult.Failure::class, name = "Failure")
    )
    private interface CommandHandlerResultMixin

    private abstract class SuccessMixin {
        companion object {
            @JvmStatic
            @JsonCreator
            fun create(): CommandHandlerResult.Success = CommandHandlerResult.Success
        }
    }

    private abstract class FailureMixin @JsonCreator constructor(val message: String)
}
