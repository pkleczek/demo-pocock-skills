# Coding standards

Apply these standards to new and modified code. Keep unrelated code unchanged. When compatibility requires a deviation, explain the constraint.

## Design and architecture

- Use a value object when a domain concept has its own meaning, validation, units, or invariants (for example, `Money`, `EmailAddress`, or `OrderId`). Keep values primitive when a wrapper would add no useful semantics.
- Keep business invariants and state transitions in behavior-rich domain objects. Use application services or facades to expose and coordinate use cases, not to hold domain rules. Prefer “Tell, Don’t Ask”: ask an object to perform behavior rather than retrieving its state to make decisions elsewhere.
- When an operation has expected business-rejection cases, model its outcomes with an algebraic data type: a Kotlin sealed interface or class with named success and rejection variants. Carry case-specific data in each variant and handle outcomes exhaustively. Reserve exceptions for unexpected technical failures and programmer errors.
- Apply DDD tactical patterns when they clarify the domain. Use entities for concepts with identity and aggregate roots to protect consistency boundaries.
- Keep source-code dependencies pointing inward: application code may depend on domain code; domain code remains independent of application code and adapters. Keep HTTP, database, and framework-specific types in adapters or configuration, never in the core.
- Define aggregate repository interfaces in the domain and implement them in outbound adapters. Keep their contracts expressed in domain terms, without application pagination or transport types. Define application-specific query ports and pagination contracts in the application layer; do not combine them into the aggregate repository. One adapter may implement both contracts when this avoids duplicated mapping. Define other outbound ports in the core layer that owns their meaning.
- Enforce business rules in the domain, not again in request annotations or application services. Inbound validation checks parsing, required fields, request shape, and transport constraints needed before domain construction. For example, validate the required property for a discriminated attendance request in the adapter, but validate title length and non-blank content in the domain. Report transport errors before invoking the use case; map domain rejections to client errors once transport validation succeeds.

## Feature-based package structure

Organize code by feature, keeping each feature’s domain and adapters together rather than grouping all controllers or repositories across the whole application.

Create only the packages and components the feature needs. Use the project’s root package and lowercase feature names; for example:

```text
<root-package>.orders
  inbound
    OrderEndpoint.kt
  application
    OrderFacade.kt
    OrderQueries.kt
  domain
    Order.kt
    OrderRepository.kt
  outbound
    JdbcOrderRepository.kt
  configuration
    OrderConfiguration.kt
```

Inbound adapters handle transport mapping and validation (such as parsing and request shape), then delegate to application use cases. Outbound adapters own SQL, protocol handling, and external-system mapping.

The application package represents the feature's application layer, not a single application service. Use-case classes (one per use case when useful) are application services; commands, results, and query ports are supporting contracts. A facade or a class named `Service` is not required. Keep framework wiring and security configuration in `configuration`, outside inbound adapters. Retain feature-first packaging rather than introducing application-wide layer packages.

## Testing

- Test observable behavior through public contracts: results, state changes, emitted events, or errors—not private implementation details.
- Use Detroit/classicist-style unit tests for domain behavior and application coordination. Exercise real domain collaborators together, using deterministic fakes or stubs at external I/O ports.
- Integration-test adapter mapping and wiring with actual adapter implementations and configuration: exercise inbound adapters through the HTTP stack, persistence adapters against a real test database, and external-service clients against a controlled protocol-level test server.
- Test each business rule through the smallest public behavioral contract that expresses it, which may span several collaborating objects. Keep business-rule cases at that boundary rather than repeating them in adapter tests.
