---
name: axon5-read-slice-tests
description: >
  Implement read slice tests (projections + query handlers) using Axon Framework 5 AxonTestFixture
  with Spring Boot integration tests. A read slice is: Events projected into a Read Model, queried via QueryGateway.
  Use when: (1) implementing tests for a new or existing read slice / projection in an AF5 project,
  (2) migrating/porting read slice tests from Axon Framework 4 (Java or Kotlin) to AF5,
  (3) user provides a read slice implementation and asks to write tests for it,
  (4) user says "test", "implement tests", "add tests" for a read slice, projection, query handler,
  or read model in an Axon Framework 5 / Vertical Slice Architecture project.
---

# Axon Framework 5 Read Slice Tests

## Step 0: Discover Target Project Conventions

Before writing any test code, read the target project's CLAUDE.md and explore at least one existing read slice test.
Look for:

- Test annotation (`@HeroesAxonSpringBootTest` or equivalent)
- Feature flag pattern (`@TestPropertySource` enabling the slice)
- Assertion library (AssertJ, AssertK, etc.)
- Test naming conventions (backtick-quoted method names)
- Metadata handling (how `gameId` or correlation IDs are attached to events)
- Existing read slice test files as patterns

## Step 1: Analyze the Read Slice Implementation

Read the slice file and extract:

| Element              | What to extract                                                    |
|----------------------|--------------------------------------------------------------------|
| **Query class**      | Name, properties (e.g., `GetAllDwellings(gameId)`)                 |
| **Result class**     | Nested result type (e.g., `GetAllDwellings.Result(items)`)         |
| **Read model**       | Entity class with all fields and their types                       |
| **Event handlers**   | Which events the projector handles (`@EventHandler` methods)       |
| **Metadata usage**   | `@MetadataValue` parameters — events MUST include this metadata    |
| **Feature flag**     | `@ConditionalOnProperty` prefix and name for `@TestPropertySource` |
| **Repository**       | JPA repository interface and its query methods                     |

## Step 2: Design Test Cases

Cover these scenarios (adapt to specific slice):

1. **Empty state**: No events published → query returns empty result
2. **Single entity**: One creation event → query returns single item
3. **Multiple entities**: Multiple creation events → query returns all items
4. **State updates**: Creation event + update event → query returns updated state
5. **Lifecycle**: Full sequence of events reflecting real usage

## Step 3: Implement the Test

Use the Spring Boot integration test approach with `AxonTestFixture`.

### Test Class Structure

```kotlin
@TestPropertySource(properties = ["slices.{context}.read.{feature}.enabled=true"])
@HeroesAxonSpringBootTest
internal class {Feature}SpringSliceTest @Autowired constructor(
    private val fixture: AxonTestFixture
) {
    // Test data
    private val gameId = GameId.random()
    private val gameMetadata = AxonMetadata.with("gameId", gameId.raw)

    // Tests...

    // Query helper
    private fun query{Feature}(cfg: Configuration): {Query}.Result =
        cfg.getComponent(QueryGateway::class.java)
            .query({Query}(gameId), {Query}.Result::class.java)
            .orTimeout(1, TimeUnit.SECONDS)
            .join()
}
```

### AxonTestFixture API for Read Slices

**Empty state test** (synchronous — no events to process):

```kotlin
fixture.`when`()
    .nothing()
    .then()
    .expect { cfg ->
        val result = queryFeature(cfg)
        assertThat(result.items).isEmpty()
    }
```

**Events-present test** (asynchronous — must await event processing):

```kotlin
fixture.given()
    .event(SomeEvent(...), gameMetadata)
    .event(AnotherEvent(...), gameMetadata)
    .then()
    .await { r ->
        r.expect { cfg ->
            val result = queryFeature(cfg)
            assertThat(result.items).containsExactlyInAnyOrder(
                ReadModel(...)
            )
        }
    }
```

### Key Rules

- **Metadata is required**: If the projector uses `@MetadataValue(GameMetadata.GAME_ID_KEY)`, every event must be
  published with metadata containing that key: `.event(payload, gameMetadata)`
- **`await` for events, `expect` for empty state**: Use `.then().await { r -> r.expect { } }` when events were given
  (async processing). Use `.then().expect { }` when no events (nothing to wait for).
- **Query via Configuration**: Access `QueryGateway` through `cfg.getComponent(QueryGateway::class.java)` inside the
  `expect` block.
- **Timeout on query**: Always add `.orTimeout(1, TimeUnit.SECONDS)` before `.join()`.
- **Assert with full objects**: Use `containsExactlyInAnyOrder(ReadModel(...))` with explicitly constructed read model
  instances rather than field-by-field assertions.
- **Explicit expected values**: Define expected values (like cost maps) as explicit properties rather than deriving them
  from domain objects. This makes tests more readable and catches serialization issues.
- **Constructor injection**: Inject `AxonTestFixture` via constructor, not field injection.

## References

- [Read Slice Test Example](references/read-slice-test-example.md) — Complete working example
  (`GetAllDwellingsSpringSliceTest`)
