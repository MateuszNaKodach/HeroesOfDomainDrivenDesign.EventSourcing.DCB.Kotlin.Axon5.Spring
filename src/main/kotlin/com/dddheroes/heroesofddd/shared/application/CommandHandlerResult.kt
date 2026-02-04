package com.dddheroes.heroesofddd.shared.application

import com.dddheroes.heroesofddd.shared.domain.DomainEvent
import com.dddheroes.heroesofddd.shared.domain.DomainRuleViolatedException
import com.dddheroes.heroesofddd.shared.domain.FailureEvent

sealed class CommandHandlerResult {
    data object Success : CommandHandlerResult()
    data class Failure(val message: String) : CommandHandlerResult()

    fun throwIfFailure(): CommandHandlerResult {
        if (this is Failure) {
            throw DomainRuleViolatedException(message)
        }
        return this
    }
}

@JvmName("resultOfTry")
inline fun <T, R> T.resultOf(block: T.() -> R): CommandHandlerResult {
    return try {
        block()
        CommandHandlerResult.Success
    } catch (e: Throwable) {
        CommandHandlerResult.Failure(e.message ?: "Unknown error")
    }
}

inline fun <T> T.resultOf(block: T.() -> CommandHandlerResult): CommandHandlerResult {
    return try {
        block()
    } catch (e: Throwable) {
        CommandHandlerResult.Failure(e.message ?: "Unknown error")
    }
}

fun <T : DomainEvent> Collection<T>.toCommandResult(): CommandHandlerResult {
    val failureEvents = this.filterIsInstance<FailureEvent>()
    return if (failureEvents.isEmpty()) {
        CommandHandlerResult.Success
    } else {
        val messages = failureEvents.joinToString(", ") { it.reason }
        CommandHandlerResult.Failure(messages)
    }
}

fun DomainEvent.toCommandResult(): CommandHandlerResult {
    return if (this is FailureEvent) {
        CommandHandlerResult.Failure(this.reason)
    } else {
        CommandHandlerResult.Success
    }
}

