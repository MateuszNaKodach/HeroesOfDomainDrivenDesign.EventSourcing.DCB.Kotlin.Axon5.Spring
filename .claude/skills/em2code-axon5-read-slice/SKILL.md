---
name: em2code-axon5-read-slice
description: >
  Implement read slices (projections + query handlers + REST API + tests) using Axon Framework 5 AxonTestFixture
  with Spring Boot integration tests. A read slice is: Events projected into a Read Model, queried via QueryGateway.
  Use when: (1) implementing a new read slice / projection in an AF5 project,
  (2) migrating/porting a read slice from Axon Framework 4 (Java or Kotlin) to AF5,
  (3) user provides a read slice specification or Event Modeling artifact and asks to implement it,
  (4) user says "implement", "create", "add" a read slice, projection, query handler,
  or read model in an Axon Framework 5 / Vertical Slice Architecture project.
---

# Axon Framework 5 Read Slice

## Step 0: Discover Target Project Conventions

Before writing any code, read the target project's CLAUDE.md and explore at least one existing read slice.
Look for:

- Test annotation (`@HeroesAxonSpringBootTest` or equivalent)
- Feature flag pattern (`@TestPropertySource` enabling the slice)
- Assertion library (AssertJ, AssertK, etc.)
- Test naming conventions (backtick-quoted method names)
- Metadata handling (how `gameId` or correlation IDs are attached to events)
- Existing read slice files and tests as patterns
- REST API test pattern (`@RestAssuredMockMvcTest`, `@AxonGatewaysMockTest`)
- Spring configuration metadata file location

## Step 1: Implement the Read Slice

A read slice file contains all layers in a single file. **Do NOT add section comments** (Domain/Application/Presentation)
for read slices — those are only for write slices.

### Slice File Structure

```kotlin
// Query DTO + Result DTO
data class GetFeature(val gameId: GameId, ...) {
    data class Result(...)
    // Optional: nested result DTOs if read model doesn't match result 1:1
}

// JPA Entity (read model) — internal to projection
@Entity @Table(name = "...", indexes = [...])
data class FeatureReadModel(...)

// Repository — private
@ConditionalOnProperty(...) @Repository
private interface FeatureReadModelRepository : JpaRepository<...> { ... }

// Projector — private
@ConditionalOnProperty(...) @Component
@SequencingPolicy(type = MetadataSequencingPolicy::class, parameters = ["gameId"])
private class FeatureReadModelProjector(...) { ... }

// Query Handler — private
@ConditionalOnProperty(...) @Component
private class FeatureReadModelQueryHandler(...) { ... }

// REST Controller — internal
@ConditionalOnProperty(...) @RestController
internal class FeatureRestApi(...) { ... }
```

### Result DTO Rules

- If the read model matches the desired query result **1:1**, expose the JPA entity directly in the `Result` for simplicity.
- If the read model contains fields the caller already knows from the query (e.g., `gameId`, `armyId`), create a
  **separate result data class** nested inside the query that strips those redundant fields. The query handler maps
  from the JPA entity to the result DTO.

### Idiomatic Kotlin in Projectors

Use `findByIdOrNull` (from `org.springframework.data.repository.findByIdOrNull`) with scope functions:

```kotlin
// Upsert pattern
val updated = repository.findByIdOrNull(id)
    ?.let { it.copy(quantity = it.quantity + event.quantity.raw) }
    ?: FeatureReadModel(...)
repository.save(updated)

// Delete-or-update pattern
repository.findByIdOrNull(id)?.let { existing ->
    if (shouldDelete) repository.deleteById(id) else repository.save(existing.copy(...))
}
```

### JPA Index

Add `@Table(indexes = [...])` for columns used in repository query methods:

```kotlin
@Table(
    name = "context_read_feature",
    indexes = [Index(name = "idx_feature_game_entity", columnList = "gameId, entityId")]
)
```

### Spring Configuration Metadata

After creating the slice, add the feature flag entry to
`src/main/resources/META-INF/additional-spring-configuration-metadata.json`:

```json
{
  "name": "slices.{context}.read.{feature}.enabled",
  "type": "java.lang.Boolean",
  "description": "Enable/disable the {Feature} read slice in the {Context} bounded context."
}
```

## Step 2: Design Test Cases

Cover these scenarios (adapt to specific slice):

1. **Empty state**: No events published → query returns empty result
2. **Single entity**: One creation event → query returns single item
3. **Multiple entities**: Multiple creation events → query returns all items
4. **State updates**: Creation event + update event → query returns updated state
5. **Aggregation**: Same entity updated multiple times → quantities/values accumulated correctly
6. **Deletion**: Entity added then fully removed → disappears from query result
7. **Isolation**: Multiple entities exist → query returns only matching ones (by ID, game, etc.)
8. **Lifecycle**: Full sequence of events reflecting real usage

## Step 3: Implement the Spring Slice Test

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

### AxonTestFixture DSL for Read Slices

**Empty state test** (synchronous — no events to process):

```kotlin
fixture.When { nothing() } Then {
    expect { cfg ->
        val result = queryFeature(cfg)
        assertThat(result.items).isEmpty()
    }
}
```

**Events-present test** (asynchronous — must await event processing):

```kotlin
fixture.Given {
    event(SomeEvent(...), gameMetadata)
    event(AnotherEvent(...), gameMetadata)
} Then {
    awaitAndExpect { cfg ->
        val result = queryFeature(cfg)
        assertThat(result.items).containsExactlyInAnyOrder(
            ExpectedResult(...)
        )
    }
}
```

### Key Rules

- **Metadata is required**: If the projector uses `@MetadataValue(GameMetadata.GAME_ID_KEY)`, every event must be
  published with metadata containing that key: `.event(payload, gameMetadata)`
- **`await` for events, `expect` for empty state**: Use `Given { } Then { awaitAndExpect { } }` when events were given
  (async processing). Use `When { nothing() } Then { expect { } }` when no events (nothing to wait for).
- **Query via Configuration**: Access `QueryGateway` through `cfg.getComponent(QueryGateway::class.java)` inside the
  `expect` block.
- **Timeout on query**: Always add `.orTimeout(1, TimeUnit.SECONDS)` before `.join()`.
- **Assert with full objects**: Use `containsExactlyInAnyOrder(ResultDto(...))` with explicitly constructed result
  instances rather than field-by-field assertions.
- **Explicit expected values**: Define expected values (like cost maps) as explicit properties rather than deriving them
  from domain objects. This makes tests more readable and catches serialization issues.
- **Constructor injection**: Inject `AxonTestFixture` via constructor, not field injection.

## Step 4: Implement the REST API Test

Use `@RestAssuredMockMvcTest` + `@AxonGatewaysMockTest` with mocked `QueryGateway`:

```kotlin
@RestAssuredMockMvcTest
@AxonGatewaysMockTest
@TestPropertySource(properties = ["slices.{context}.read.{feature}.enabled=true"])
internal class {Feature}RestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    private val gameId = GameId.random()

    @Test
    fun `returns result`() {
        val query = GetFeature(gameId)
        val result = GetFeature.Result(listOf(...))
        gateways.assumeQueryReturns(query, result)

        Given {
            pathParam("gameId", gameId.raw)
        } When {
            async().get("/games/{gameId}/feature")
        } Then {
            statusCode(HttpStatus.OK.value())
            contentType(ContentType.JSON)
            body("items", hasSize<Int>(1))
        }
    }

    @Test
    fun `returns empty when no data`() {
        val query = GetFeature(gameId)
        val result = GetFeature.Result(emptyList())
        gateways.assumeQueryReturns(query, result)

        Given {
            pathParam("gameId", gameId.raw)
        } When {
            async().get("/games/{gameId}/feature")
        } Then {
            statusCode(HttpStatus.OK.value())
            body("items", hasSize<Int>(0))
        }
    }
}
```

### Key Rules for REST API Tests

- **Mock the query, not the projection**: Use `gateways.assumeQueryReturns(query, result)` to stub the `QueryGateway`.
- **Match the exact query instance**: The mock uses `eq(query)`, so construct the same query the controller will create
  from path variables.
- **Use `async()`**: Controllers returning `CompletableFuture` require `async().get(...)` in RestAssured.

## References

- [Read Slice Test Example](references/read-slice-test-example.md) — Complete working example
  (`GetAllDwellingsSpringSliceTest`)
