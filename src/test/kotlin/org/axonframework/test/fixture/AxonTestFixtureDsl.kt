@file:Suppress("TestFunctionName")

package org.axonframework.test.fixture

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.common.configuration.Configuration
import org.axonframework.messaging.core.Metadata
import org.axonframework.test.fixture.AxonTestPhase.*
import java.time.Duration

/**
 * Kotlin DSL for [AxonTestFixture] providing `Given { } When { } Then { }` syntax.
 *
 * Mirrors the REST Assured Kotlin DSL pattern — no wrapper classes, just extension functions
 * on Axon's own types. Eliminates the need for backtick-escaping `` `when`() `` and provides
 * lambda-with-receiver blocks for each test phase.
 *
 * ### Write slice example:
 * ```kotlin
 * sliceUnderTest.Scenario {
 *     Given {
 *         noPriorActivity()
 *     } When {
 *         command(BuildDwelling(...), gameMetadata)
 *     } Then {
 *         resultMessagePayload(CommandHandlerResult.Success)
 *         events(DwellingBuilt(...))
 *     }
 * }
 * ```
 *
 * ### Read slice example (Given → Then, skip When):
 * ```kotlin
 * fixture.Given {
 *     event(DwellingBuilt(...), gameMetadata)
 * } Then {
 *     awaitAndExpect { cfg -> ... }
 * }
 * ```
 *
 * ### Read slice example (skip Given):
 * ```kotlin
 * fixture.When { nothing() } Then { expect { cfg -> ... } }
 * ```
 */

// ── Scenario Wrapper ──────────────────────────────────────────

/**
 * Wraps a full Given-When-Then test scenario for readability.
 * Scopes the receiver to [AxonTestFixture] so that [Given], [When] entry points are available.
 *
 * @param description optional human-readable description (for documentation purposes only)
 * @param block the scenario body using `Given { } When { } Then { }` DSL
 */
@Suppress("unused", "UNUSED_PARAMETER")
fun AxonTestFixture.Scenario(
    description: String = "",
    block: AxonTestFixture.() -> Unit
) {
    this.block()
}

// ── Entry Points ──────────────────────────────────────────────

/**
 * Enters the Given phase to define initial state before the action under test.
 *
 * Inside the [block], call methods like [Given.noPriorActivity], [Given.event], [Given.events],
 * [Given.command], or [Given.commands] to set up preconditions.
 *
 * Chain with [Given.When] or [Given.Then] using infix syntax:
 * ```kotlin
 * fixture.Given { noPriorActivity() } When { command(...) } Then { ... }
 * ```
 */
fun AxonTestFixture.Given(block: Given.() -> Unit): Given =
    this.given().apply(block)

/**
 * Enters the When phase directly, skipping the Given phase (no prior state).
 *
 * The return type [T] is inferred from the block — e.g. [When.command] returns [When.Command],
 * preserving compile-time type safety for the subsequent [Then] call.
 *
 * ```kotlin
 * fixture.When { nothing() } Then { expect { cfg -> ... } }
 * ```
 */
@Suppress("unused")
fun <T> AxonTestFixture.When(block: When.() -> T): T =
    this.`when`().block()

// ── Given → When ──────────────────────────────────────────────

/**
 * Transitions from the Given phase to the When phase.
 *
 * The generic return type [T] is inferred from the block's return value
 * (e.g. [When.Command], [When.Event], [When.Nothing]), so the correct
 * type-specific [Then] overload resolves at compile time.
 */
infix fun <T> Given.When(block: When.() -> T): T =
    this.`when`().block()

// ── Given → Then (skip When, for read model tests) ───────────

/**
 * Transitions from the Given phase directly to the Then phase, skipping the When phase.
 *
 * Useful for read model / projection tests where you only need to set up events
 * and assert on the projected state:
 * ```kotlin
 * fixture.Given { event(DwellingBuilt(...), meta) } Then {
 *     awaitAndExpect { cfg -> ... }
 * }
 * ```
 */
@Suppress("unused")
infix fun Given.Then(block: Then.Nothing.() -> Unit): Then.Nothing =
    this.then().apply(block)

// ── When.X → Then (type-specific) ────────────────────────────

/**
 * Transitions from a command When phase to the Then phase for command-specific assertions.
 *
 * Inside the [block], command-specific methods like [Then.Command.resultMessagePayload],
 * [Then.Command.success], and all [Then.MessageAssertions] methods are available.
 */
infix fun When.Command.Then(block: Then.Command.() -> Unit): Then.Command =
    this.then().apply(block)

/**
 * Transitions from an event When phase to the Then phase for event-specific assertions.
 */
@Suppress("unused")
infix fun When.Event.Then(block: Then.Event.() -> Unit): Then.Event =
    this.then().apply(block)

/**
 * Transitions from a no-op When phase to the Then phase.
 */
@Suppress("unused")
infix fun When.Nothing.Then(block: Then.Nothing.() -> Unit): Then.Nothing =
    this.then().apply(block)

// ── Kotlin Helpers ────────────────────────────────────────────

/**
 * Combines [Then.Message.await] and [Then.MessageAssertions.expect] into a single call.
 *
 * Equivalent to `await { it.expect(block) }` with the default 5-second timeout.
 *
 * ```kotlin
 * fixture.Given { event(...) } Then {
 *     awaitAndExpect { cfg ->
 *         val result = cfg.getComponent(QueryGateway::class.java).query(...).join()
 *         assertThat(result).isEqualTo(expected)
 *     }
 * }
 * ```
 */
@Suppress("unused")
fun <T : Then.Message<T>> T.awaitAndExpect(block: (Configuration) -> Unit): T =
    this.await { it.expect(block) }

/**
 * Combines [Then.Message.await] and [Then.MessageAssertions.expect] with a custom [timeout].
 *
 * @param timeout maximum duration to wait for the assertion to pass
 * @param block assertion receiving the [Configuration] for component lookups
 */
@Suppress("unused")
fun <T : Then.Message<T>> T.awaitAndExpect(
    timeout: Duration,
    block: (Configuration) -> Unit
): T = this.await({ it.expect(block) }, timeout)

/**
 * Reified variant of [Then.MessageAssertions.exception] — avoids `::class.java` boilerplate.
 *
 * ```kotlin
 * Then { exception<InsufficientBalanceException>() }
 * ```
 */
@Suppress("unused")
inline fun <reified T : Throwable> Then.MessageAssertions<*>.exception() {
    this.exception(T::class.java)
}

/**
 * Reified variant of [Then.Command.resultMessagePayloadSatisfies] — infers the payload type
 * from the generic parameter and handles conversion automatically.
 *
 * ```kotlin
 * Then { resultMessagePayloadSatisfies<MyResult> { assertThat(it.value).isEqualTo(42) } }
 * ```
 */
@Suppress("unused", "DEPRECATION")
inline fun <reified T> Then.Command.resultMessagePayloadSatisfies(
    noinline consumer: (T) -> Unit
) {
    this.resultMessagePayloadSatisfies(T::class.java, consumer)
}

// ── Metadata Assertions ───────────────────────────────────────

/**
 * Asserts that all published events contain the [expected] metadata entries.
 *
 * Uses a subset check — events may carry additional metadata beyond [expected].
 * Provides per-event error messages with the event payload type for easy debugging.
 *
 * ```kotlin
 * Then {
 *     events(DwellingBuilt(...))
 *     allEventsHaveMetadata(gameMetadata)
 * }
 * ```
 */
@Suppress("unused")
fun <T : Then.MessageAssertions<T>> T.allEventsHaveMetadata(expected: Metadata): T =
    this.eventsSatisfy { events ->
        assertThat(events)
            .`as`("all events should contain metadata %s", expected)
            .allSatisfy { event ->
                assertThat(event.metadata())
                    .`as`("metadata of '%s' event", event.payloadType().simpleName)
                    .containsAllEntriesOf(expected)
            }
    }
