# Trackr Style Guide

## Tests

### Use hardcoded values, not runtime state, in test inputs

Tests that derive values from the current time can behave differently depending on when they run — day boundaries, millisecond ties, and reproducibility all become problems. Use a fixed anchor instead.

```kotlin
// avoid
val today = Instant.now()
val yesterday = today.minusSeconds(86_400)

// prefer
val anchor = Instant.parse("2024-01-15T12:00:00Z")
val dayBefore = anchor.minusSeconds(86_400)
```

The same applies to random numbers, locale-sensitive formatting, and any other value derived from process or system state. When a test genuinely needs to interact with the current time (e.g., testing expiry logic), inject a `Clock` and fix it in the test rather than reading the real clock.
