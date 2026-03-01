package com.dddheroes.heroesofddd

import com.dddheroes.extensions.webmvc.test.AxonMockMvc
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import java.time.Instant

/**
 * Heroes-of-DDD–specific decorator around the generic [AxonMockMvc].
 *
 * Adds [CommandHandlerResult]-based convenience methods while delegating
 * all generic command, query, and clock stubbing to [AxonMockMvc].
 *
 * ## Convenience — [CommandHandlerResult] shortcuts
 *
 * ```kotlin
 * axonMockMvc.assumeCommandSuccess<BuildDwelling>()
 * axonMockMvc.assumeCommandFailure<BuildDwelling>("Already built")
 *
 * val command = BuildDwelling(dwellingId, creatureId, cost)
 * axonMockMvc.assumeCommandSuccess(command)
 * axonMockMvc.assumeCommandFailure(command, "Already built")
 * ```
 *
 * ## Generic methods (delegated from [AxonMockMvc])
 *
 * ```kotlin
 * axonMockMvc.assumeCommandReturns<BuildDwelling>(MyResult("ok"))
 * axonMockMvc.assumeCommandException<BuildDwelling>(IllegalStateException("boom"))
 * axonMockMvc.assumeQueryReturns(GetDwellingById(id), DwellingView(...))
 * axonMockMvc.currentTimeIs(Instant.parse("2024-01-15T10:00:00Z"))
 * ```
 *
 * @see AxonMockMvc
 * @see HeroesAxonWebMvcTest
 */
class HeroesAxonMockMvc(@PublishedApi internal val delegate: AxonMockMvc) {

    // ---- Delegated generic methods ----

    /** @see AxonMockMvc.assumeCommandReturns */
    fun <C : Any> assumeCommandReturns(command: C, result: Any) =
        delegate.assumeCommandReturns(command, result)

    /** @see AxonMockMvc.assumeCommandReturns */
    inline fun <reified C : Any> assumeCommandReturns(result: Any) =
        delegate.assumeCommandReturns<C>(result)

    /** @see AxonMockMvc.assumeCommandException */
    fun <C : Any> assumeCommandException(command: C, exception: Exception) =
        delegate.assumeCommandException(command, exception)

    /** @see AxonMockMvc.assumeCommandException */
    inline fun <reified C : Any> assumeCommandException(exception: Exception) =
        delegate.assumeCommandException<C>(exception)

    /** @see AxonMockMvc.assumeQueryReturns */
    inline fun <reified R, reified Q : Any> assumeQueryReturns(query: Q, result: R) =
        delegate.assumeQueryReturns(query, result)

    /** @see AxonMockMvc.currentTimeIs */
    fun currentTimeIs(instant: Instant): Instant = delegate.currentTimeIs(instant)

    /** @see AxonMockMvc.currentTime */
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
