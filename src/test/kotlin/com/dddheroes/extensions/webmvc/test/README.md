# @AxonWebMvcTest

Composition-based test infrastructure for Axon Framework 5 REST API controller tests.

## Quick Start

```kotlin
@AxonWebMvcTest
@TestPropertySource(properties = ["slices.myslice.write.mycommand.enabled=true"])
class MyControllerRestApiTest {

    @Autowired
    lateinit var axonMockMvc: AxonMockMvc

    @Test
    fun `command success - returns 204 No Content`() {
        axonMockMvc.assumeCommandSuccess<MyCommand>()

        Given {
            pathParam("id", "123")
            contentType(ContentType.JSON)
            body("""{"field": "value"}""")
        } When {
            async().put("/my-resource/{id}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }
}
```

## What `@AxonWebMvcTest` Provides

A single annotation that replaces `@WebMvcTest` and sets up everything needed for Axon REST API
tests:

| Bean                | Source            | Purpose                                   |
|---------------------|-------------------|-------------------------------------------|
| `MockMvc`           | `@WebMvcTest`     | Spring MVC test support                   |
| `CommandGateway`    | `@MockitoBean`    | Mocked — stubbed via `AxonMockMvc`        |
| `QueryGateway`      | `@MockitoBean`    | Mocked — stubbed via `AxonMockMvc`        |
| `Clock`             | `@MockitoBean`    | Mocked — controlled via `AxonMockMvc`     |
| `AxonMockMvc`       | `@TestConfiguration` | Test helper with all stubbing methods  |

All mocks are automatically reset after each test by Spring's `MockitoResetTestExecutionListener`.
RestAssured and clock are re-configured before each test by `AxonMockMvcSetupListener`.

## `AxonMockMvc` API

### Command Stubbing

#### Generic — any result type

```kotlin
// By type — matches ANY command of this type
axonMockMvc.assumeCommandReturns<BuildDwelling>(MyResult("ok"))

// By instance — matches this specific command (eq() matcher)
axonMockMvc.assumeCommandReturns(specificCommand, MyResult("ok"))
```

#### Exception — failed future / thrown exception

Unlike `assumeCommandFailure` (which returns a **successful** future carrying a failure payload),
`assumeCommandException` makes `send()` return a **failed** `CompletableFuture` and `sendAndWait()`
throw the exception directly:

```kotlin
// By type — matches ANY command of this type
axonMockMvc.assumeCommandException<BuildDwelling>(IllegalStateException("aggregate not found"))

// By instance — matches this specific command (eq() matcher)
axonMockMvc.assumeCommandException(specificCommand, IllegalStateException("aggregate not found"))
```

| Method | `send()` returns | `sendAndWait()` does |
|---|---|---|
| `assumeCommandFailure` | Completed future with `Failure` payload | Returns `Failure` payload |
| `assumeCommandException` | Failed future with exception | Throws the exception |

#### Convenience — `CommandHandlerResult` shortcuts

```kotlin
// By type — matches ANY command of this type
axonMockMvc.assumeCommandSuccess<BuildDwelling>()
axonMockMvc.assumeCommandFailure<BuildDwelling>("Already built")

// By instance — matches this specific command (eq() matcher)
val command = BuildDwelling(dwellingId, creatureId, cost)
axonMockMvc.assumeCommandSuccess(command)
axonMockMvc.assumeCommandFailure(command, "Already built")
```

The failure message defaults to `"Simulated failure"` if omitted.

Each stub covers all AF5 `CommandGateway` dispatch methods:

- `send(command, metadata)` — async with metadata (most common in controllers)
- `send(command)` — async without metadata
- `sendAndWait(command)` — synchronous
- `sendAndWait(command, resultType)` — synchronous typed

### Query Stubbing

```kotlin
axonMockMvc.assumeQueryReturns(
    GetDwellingById(dwellingId),
    DwellingView(dwellingId, creatureId, availableCreatures = 5)
)
```

### Clock Control

A default time (`Instant.now()`) is set before each test. Override it for deterministic tests:

```kotlin
val fixedTime = Instant.parse("2024-01-15T10:00:00Z")
axonMockMvc.currentTimeIs(fixedTime)

// Read back the current mocked time
val now = axonMockMvc.currentTime()
```

This sets both the Mockito `Clock` mock and `GenericEventMessage.clock`.

## Async Controllers

Controllers that return `CompletableFuture<ResponseEntity<*>>` (the standard AF5 pattern with
`commandGateway.send(...).resultAs(...).toResponseEntity()`) require `async()` dispatch in
RestAssured:

```kotlin
} When {
    async().put("/my-resource/{id}")   // not just put(...)
} Then {
    statusCode(HttpStatus.NO_CONTENT.value())
}
```

## Forwarded `@WebMvcTest` Attributes

All key `@WebMvcTest` attributes are available on `@AxonWebMvcTest`:

```kotlin
@AxonWebMvcTest(
    controllers = [MyController::class],         // limit to specific controllers
    properties = ["my.property=value"],           // test properties
    useDefaultFilters = false,                    // disable component scan filters
    excludeAutoConfiguration = [SomeConfig::class] // exclude auto-configurations
)
```

For conditional slice properties, `@TestPropertySource` is recommended (consistent with the
project's slice architecture):

```kotlin
@AxonWebMvcTest
@TestPropertySource(properties = ["slices.creaturerecruitment.write.builddwelling.enabled=true"])
```

## Design

```
@AxonWebMvcTest (annotation)
├── @WebMvcTest                          — Spring MVC test slice
├── @MockitoBeans                        — mocks for CommandGateway, QueryGateway, Clock
├── @Import(AxonMockMvcConfiguration)    — registers AxonMockMvc bean
└── @TestExecutionListeners
    └── AxonMockMvcSetupListener         — calls AxonMockMvc.setUp() before each test
```
