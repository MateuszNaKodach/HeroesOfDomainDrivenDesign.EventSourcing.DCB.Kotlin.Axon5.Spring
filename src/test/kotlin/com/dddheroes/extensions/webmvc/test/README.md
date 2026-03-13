# Test Extensions

Composable test annotations for Axon Framework 5 + Spring Boot. Two independent concerns:

1. **Gateway mocking** (`@AxonGatewaysMockTest`) — stub `CommandGateway`, `QueryGateway`, `Clock`
2. **RestAssured MockMvc** (`@RestAssuredMockMvcTest`) — `@WebMvcTest` + automatic RestAssured setup

Each can be used independently or together.

## Package structure

```
org.axonframework.extensions.spring.test      ← generic gateway mocking
└── AxonGatewaysMock.kt                        class + @AxonGatewaysMockTest + config + listener

com.dddheroes.extensions.webmvc.test          ← RestAssured + @WebMvcTest
└── RestAssuredMockMvcTest.kt                  @RestAssuredMockMvcTest + listener
```

## `@AxonGatewaysMockTest`

Provides mocked `CommandGateway`, `QueryGateway`, and `Clock` via `@MockitoBean`, plus an
`AxonGatewaysMock` bean for stubbing them. Works with any test annotation.

```kotlin
@AxonGatewaysMockTest
@SpringBootTest
class SomeIntegrationTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    @Test
    fun `stub command`() {
        gateways.assumeCommandReturns<MyCommand>(MyResult("ok"))
        // ...
    }
}
```

### Command stubbing

```kotlin
// By type — matches any command of that type
gateways.assumeCommandReturns<BuildDwelling>(MyResult("ok"))

// By instance — matches the specific command
gateways.assumeCommandReturns(specificCommand, MyResult("ok"))
```

Each stub covers all AF5 `CommandGateway` invocation styles:
`send(command, metadata)`, `send(command)`, `sendAndWait(command)`, `sendAndWait(command, resultType)`.

### Command exception (failed future)

```kotlin
gateways.assumeCommandException<BuildDwelling>(IllegalStateException("boom"))
gateways.assumeCommandException(specificCommand, IllegalStateException("boom"))
```

Makes `send()` return a failed `CompletableFuture` and `sendAndWait()` throw the exception.

### Query stubbing

```kotlin
gateways.assumeQueryReturns(GetDwellingById(id), DwellingView(...))
```

### Clock control

```kotlin
val now = gateways.currentTimeIs(Instant.parse("2024-01-15T10:00:00Z"))
val current = gateways.currentTime()
```

Clock is automatically reset to `Instant.now()` before each test.

## `@RestAssuredMockMvcTest`

Combines `@WebMvcTest` with automatic `RestAssuredMockMvc.mockMvc(mockMvc)` setup before each
test. No `@BeforeEach` boilerplate needed. Forwards all `@WebMvcTest` attributes.

```kotlin
@RestAssuredMockMvcTest
@AxonGatewaysMockTest
class MyRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    @Test
    fun `returns 200`() {
        gateways.assumeCommandReturns<MyCommand>(MyResult("ok"))
        Given { ... } When { get("/endpoint") } Then { statusCode(200) }
    }
}
```

## Full example

```kotlin
@RestAssuredMockMvcTest
@AxonGatewaysMockTest
@TestPropertySource(properties = ["slices.creaturerecruitment.write.builddwelling.enabled=true"])
internal class BuildDwellingRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    @Test
    fun `command success - returns 204 No Content`() {
        gateways.assumeCommandReturns<BuildDwelling>(Success)
        Given { ... } When { async().put("/games/{gameId}/dwellings/{dwellingId}") } Then { statusCode(204) }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        gateways.assumeCommandReturns<BuildDwelling>(Failure("Dwelling already built"))
        Given { ... } When { async().put("/games/{gameId}/dwellings/{dwellingId}") } Then { statusCode(400) }
    }
}
```

## Design principles

- **Composition over inheritance** — annotations are composable, no base test classes
- **Separation of concerns** — gateway mocking is independent of MockMvc/RestAssured
- **No `@BeforeEach` boilerplate** — `TestExecutionListener`s handle setup automatically
- **Generic payload** — `assumeCommandReturns` accepts any result type, no framework-specific wrappers
