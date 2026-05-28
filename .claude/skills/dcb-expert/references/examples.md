# DCB Examples

## Table of Contents

1. [Course Subscriptions](#course-subscriptions) — cross-entity constraints
2. [Unique Username](#unique-username) — global uniqueness enforcement
3. [Dynamic Product Price](#dynamic-product-price) — price validation with grace periods
4. [Event-Sourced Aggregate](#event-sourced-aggregate) — traditional Aggregate with DCB
5. [Invoice Number](#invoice-number) — monotonic gapless sequences
6. [Opt-In Token](#opt-in-token) — double opt-in without Read Models
7. [Prevent Record Duplication](#prevent-record-duplication) — idempotency via token tags

---

## Course Subscriptions

**Challenge:** Students subscribe to courses with hard constraints:
- A course cannot accept more than N students (capacity)
- Course capacity can change at any time
- A student cannot subscribe to more than 10 courses

**Why traditional approaches struggle:**
- Eventual consistency — turns invariant into soft constraint
- Larger Aggregate — spans all courses and students, kills parallelism
- Reservation Pattern / Saga — complexity, compensating events, invalid intermediate states

**DCB approach:** Tag `StudentSubscribedToCourse` with BOTH `course:{courseId}` AND `student:{studentId}`.

**Events:**
- `CourseDefined` → tags: `course:{courseId}`
- `CourseCapacityChanged` → tags: `course:{courseId}`
- `StudentSubscribedToCourse` → tags: `student:{studentId}`, `course:{courseId}`

**Projections:**
- `courseExists(courseId)` — filters by `course:{courseId}`, handles `CourseDefined`
- `courseCapacity(courseId)` — filters by `course:{courseId}`, handles `CourseDefined`, `CourseCapacityChanged`
- `numberOfCourseSubscriptions(courseId)` — filters by `course:{courseId}`, counts `StudentSubscribedToCourse`
- `numberOfStudentSubscriptions(studentId)` — filters by `student:{studentId}`, counts `StudentSubscribedToCourse`
- `studentAlreadySubscribed(studentId, courseId)` — filters by BOTH tags

**Command handler `subscribeStudentToCourse`:**
```
decisionModel = compose(courseExists, courseCapacity, numberOfCourseSubscriptions, numberOfStudentSubscriptions, studentAlreadySubscribed)
checks:
  - course must exist
  - numberOfCourseSubscriptions < courseCapacity
  - student not already subscribed
  - numberOfStudentSubscriptions < 5
→ emit StudentSubscribedToCourse
```

---

## Unique Username

**Challenge:** Enforce globally unique usernames in an event-driven system.

**Features built incrementally:**

### Feature 1: Basic uniqueness
- `AccountRegistered` → tags: `username:{username}`
- Projection `isUsernameClaimed(username)`: `AccountRegistered → true`
- Command handler rejects if username already claimed

### Feature 2: Release on account closure
- `AccountClosed` → tags: `username:{username}`
- Updated projection: `AccountRegistered → true`, `AccountClosed → false`

### Feature 3: Username changes
- `UsernameChanged` → tags: `username:{oldUsername}`, `username:{newUsername}` (tagged with BOTH)
- Updated projection: `UsernameChanged → event.data.newUsername === username`

### Feature 4: Retention period
- Usernames not immediately available after release — configurable delay (e.g., 3 days)
- Projection uses event metadata (timestamp/daysAgo) to determine if retention period passed:
  ```
  AccountClosed → event.metadata?.daysAgo <= 3  (still claimed during retention)
  UsernameChanged → event.data.newUsername === username || event.metadata?.daysAgo <= 3
  ```

---

## Dynamic Product Price

**Challenge:** Purchase products ensuring displayed price is valid, with grace period for price changes.

### Feature 1: Single product, fixed price
- `ProductDefined` → tags: `product:{productId}`
- `ProductOrdered` → tags: `product:{productId}`
- Projection `ProductPriceProjection(productId)`: returns current price
- Command handler rejects if `displayedPrice !== currentPrice`

### Feature 2: Price changes with grace period
- `ProductPriceChanged` → tags: `product:{productId}`
- Projection tracks `lastValidOldPrice` and `validNewPrices[]` using event age:
  - Events within grace period (e.g., 10 min) → price added to validNewPrices
  - Events older than grace period → set as lastValidOldPrice
- Command handler accepts if displayed price matches lastValidOldPrice OR is in validNewPrices

### Feature 3: Multiple products (shopping cart)
- `ProductsOrdered` → tags: `product:{productId}` for EACH item
- Decision Model composed dynamically from `ProductPriceProjection` for each item:
  ```js
  buildDecisionModel(eventStore,
    command.items.reduce((models, item) => {
      models[item.productId] = ProductPriceProjection(item.productId)
      return models
    }, {})
  )
  ```
- Validates ALL product prices in single consistency boundary

---

## Event-Sourced Aggregate

**Challenge:** Implement traditional Event-Sourced Aggregate compatible with DCB Event Store.

**Key insight:** The Aggregate itself doesn't change — only the Repository does.

**Traditional Repository** (stream-based):
```
load(courseId):
  streamName = "course-{courseId}"
  events = eventStore.readStream(streamName)
  return CourseAggregate.reconstitute(events)

save(course):
  streamName = "course-{course.id}"
  eventStore.appendToStream(streamName, course.pullRecordedEvents(), {streamState: course.version})
```

**DCB Repository** (tag-based):
```
load(courseId):
  tags = ["course:{courseId}"]
  query = createQuery([{ tags }])
  events = eventStore.read(query)
  return CourseAggregate.reconstitute(events)

save(course):
  tags = ["course:{course.id}"]
  query = createQuery([{ tags }])
  eventsWithTags = course.pullRecordedEvents().map(e => ({ ...e, tags }))
  eventStore.append(eventsWithTags, { failIfEventsMatch: query, after: course.version })
```

---

## Invoice Number

**Challenge:** Create invoices with unique numbers forming an unbroken sequence.

**Basic approach:**
- `InvoiceCreated` → tags: `invoice:{invoiceNumber}`
- Projection `nextInvoiceNumber`: `InvoiceCreated → event.data.invoiceNumber + 1`, initialState: 1
- Command handler uses projected next number

**Performance optimization:** Loading ALL past InvoiceCreated events is inefficient. Options:
1. **Snapshots** — reduces events to load but adds complexity
2. **Only load last event** — some DCB Event Stores support `onlyLastEvent: true` or reading backwards with limit:
   ```js
   const lastEvent = eventStore.read(projection.query, { backwards: true, limit: 1 }).first()
   ```

---

## Opt-In Token

**Challenge:** Double opt-in (email confirmation) without Read Models or cryptography.

### Feature 1: Simple OTP
- `SignUpInitiated` → tags: `email:{emailAddress}`, `otp:{otp}`
- `SignUpConfirmed` → tags: `email:{emailAddress}`, `otp:{otp}`
- Projection `pendingSignUp(emailAddress, otp)`:
  - `SignUpInitiated → { data: event.data, otpUsed: false }`
  - `SignUpConfirmed → { ...state, otpUsed: true }`
- Command handler checks: pending sign-up exists AND OTP not already used

### Feature 2: Expiring OTP (60 minutes)
- Extended projection adds `otpExpired`:
  - `SignUpInitiated → { ..., otpExpired: event.metadata?.minutesAgo > 60 }`
- Command handler additionally checks OTP not expired

---

## Prevent Record Duplication

**Challenge:** Prevent duplicate processing on retried requests (idempotency).

**Approach:** Client generates an idempotency token included in the command.

- `OrderPlaced` → tags: `order:{orderId}`, `idempotency:{idempotencyToken}`
- Projection `idempotencyTokenWasUsed(idempotencyToken)`:
  - `OrderPlaced → true`, initialState: false
  - tagFilter: `idempotency:{idempotencyToken}`
- Command handler rejects if token already used

This separates domain identifiers from idempotency concerns. The server controls entity IDs while the client provides the idempotency token.
