# JHusk Adversarial Test Suite

An independent test suite written against JHusk's public API only, with no
knowledge of or access to its internals. It consumes the published Maven
Central artifact as an ordinary dependency, the same way any real user's
project would, rather than testing against JHusk's own source tree.

The goal is to catch the kind of thing a library's own author is least
likely to catch: blind spots baked into the same mental model that produced
the implementation in the first place. JHusk's own test suite is
comprehensive, but it shares that risk by construction. This suite exists
to check from the outside.

## Methodology

50 scenarios across 7 categories, run with plain JUnit 5 and Maven against
`io.github.nikhilvirdi:jhusk` as a normal test-scope dependency. Every
scenario asserts on documented, public behavior only, either a claim made
in JHusk's own README, or a reasonable expectation for any property-based
testing library to satisfy.

## Categories

- **Boundary values and type composition** (scenarios 1-8) -- integer
  range edges, exact-size collections, generic type inference across
  chained generators.
- **Impossible generators and malformed usage** (9-15) -- filters and
  generators that can never be satisfied must fail fast with the correct
  exception type, not hang.
- **Planted bugs** (16-20) -- deliberately buggy reference implementations
  (off-by-one clamp, incomplete dedup, misclassified primality, incomplete
  set union), confirming JHusk's shrinker actually finds and minimizes real
  failures.
- **Documented claim verification** (21-30) -- direct tests of specific
  sentences in JHusk's README and Javadoc, not just general behavior.
- **Stateful testing and `assuming()`** (31-38) -- the `Command`/`Commands`
  abstraction and the `assuming()` precondition primitive, both new in
  1.1.0.
- **Edge-case corpus and `exhaustive()`** (39-44) -- the deterministic
  all-zero/all-`0xFF` corpus every `check()` call runs, and the precise,
  limited coverage guarantee `exhaustive()` actually makes (only the first
  and last supplied values, not true N-way enumeration).
- **Multi-feature stress tests** (45-50) -- combinations of timeout,
  custom generation budget, `assuming()`, and `Commands` configured
  together on a single property.

## Results

**Status (tested against `io.github.nikhilvirdi:jhusk:1.1.0`): 49 of 50
passing.**

The one failure, scenario 29, is not a flaw in the test. It documents a
real defect this suite found in JHusk 1.1.0. The fix is already written
and merged into JHusk's source; it ships in the next release. Once that
release is out, this suite will be re-run against it and this section
updated.

## The defect this suite found

`Generator.filter()` returns `null` and marks its `DataSource` `INVALID`
when its retry budget is exhausted, a documented, intentional behavior.
`Generator.flatMap()`'s implementation never checked that status before
invoking its function on whatever `filter()` handed back, including a
`null` from an exhausted budget.

In practice: JHusk 1.1.0 introduced a deterministic edge-case corpus, an
all-zero and an all-`0xFF` byte buffer, run automatically before any random
generation on every single `check()` call. On the all-zero buffer, a
`filter()` that happens to reject the exact value every upstream
primitive's shrink target decodes to will exhaust its retry budget on
every single attempt, since the all-zero buffer produces the identical
input on every retry. The `null` that comes back then gets handed straight
to `flatMap()`'s function, which typically throws on it, most often a
`NullPointerException`, misreported as `GeneratorCrashException` instead
of the ordinary invalid run it actually is.

Any `.filter(predicate).flatMap(...)` chain where the predicate rejects a
value at or near a shrink target will crash this way, deterministically,
on every `check()` call, for every seed. Scenario 29 constructs exactly
this case (`filter(dims -> dims[0] != dims[1])` composed with `flatMap`,
where both integer generators share a minimum of 1) and confirms the
crash.

The fix: `flatMap()` now checks `source.getStatus()` before invoking its
function, short-circuiting on any non-`VALID` status exactly like every
other loop in `Property.check()` already does for every other generator.

## Running the suite yourself

```
mvn test
```

Requires JDK 17 or later. No local JHusk checkout needed, the suite pulls
its dependency from Maven Central like any other project would.

## Relationship to JHusk

This is a separate repository from [JHusk](https://github.com/nikhilvirdi/JHusk)
itself, deliberately. Keeping it separate is what makes "tested as an
external dependency" a fact about how this suite is built, not just a
description of intent.