# JHusk vs jqwik: a scenario-by-scenario comparison

This document reports what happened when the 50 scenarios in this suite were run against both JHusk and jqwik, side by side, on the same bugs and the same claims. It's a record of results, not an argument for either library.

## Methodology

All 50 scenarios were ported to jqwik 1.9.0 wherever the two libraries' APIs allow a genuinely fair, like-for-like test. Both libraries were consumed as ordinary external Maven dependencies, JHusk from its published 1.1.1 artifact on Maven Central, jqwik the same way, pinned to 1.9.0 for a stable comparison target. Neither library's own source was touched or inspected as part of this comparison; both were tested purely through their public API, the same way any real project would use them.

## A note on history

This isn't the first comparison run in this project's history. An earlier five-round comparison, conducted before this suite existed, found two real gaps in JHusk: it missed a boundary-condition bug that jqwik's edge-case injection caught almost immediately, and its shrinker produced a noticeably worse result on a list of composite values, burning far more attempts for a bigger, messier answer than jqwik found. Both were fixed in JHusk 1.1.0, specifically the deterministic edge-case corpus and a delta-debugging shrink pass, before a single scenario in this suite was written.

That history matters for reading the table below. The near-total parity documented here isn't a coincidence; it's largely downstream of those two fixes landing first. A comparison run before 1.1.0 would likely have looked different.

## Results by category

| Category | Scenarios | Outcome | Why |
|---|---|---|---|
| Boundary reachability & type inference | 1, 2, 3, 5, 7, 8 | Tie | Both generated the same singleton and full-range values, both correctly inferred types through chained `map`/`flatMap`/`combine` compositions. |
| Impossible filters | 9, 10 | Tie | Both failed fast with the correct exception (`FilterExhaustedException` / `TooManyFilterMissesException`) rather than hanging. |
| Oversized generation | 11 | Real difference, not a win either way | JHusk rejected a 40,000-element list at its 8KB default generation budget; jqwik generated the same list without issue. This reflects a genuine design tradeoff, JHusk bounds its byte-stream size by default and makes that bound configurable, jqwik has no equivalent architecture to bound. Neither behavior is a defect. |
| Generator crash propagation | 12 | Tie | Both surfaced the underlying exception from a generator that throws during execution, rather than swallowing it. |
| Range validation | 4 | JHusk | `Generators.integers(10, 5)` throws `IllegalArgumentException` immediately. `Arbitraries.integers().between(10, 5)` did not throw in any run of this suite. |
| Negative size validation | 6 | Tie | Both reject a negative minimum size eagerly, at construction time. |
| Null surfacing through `map` | 15 | Tie | Both correctly surfaced a `null` produced by a `map` function to the assertion, rather than masking it. |
| Planted bugs | 16, 17, 18, 19, 20 | Tie | Both caught all five deliberately planted bugs (an off-by-one clamp, a non-adjacent dedup failure, a poison list value, a prime misclassification, an incomplete set union) and shrank each to a minimal or near-minimal failing case. |
| Claim verification | 21, 22, 23, 24, 25, 27, 29, 30 | Tie | Covers shrink-toward-minimum behavior, same-seed reproducibility, concurrent generation safety, and coherent shrinking through deep generator composition. Both libraries matched on every scenario in this group. |
| Near-impossible filter | 26 | Inconclusive | Passed in one run and failed in another, with no fixed seed and the same code both times. Not re-diagnosed; reported as observed rather than resolved. |
| Stateful sequence testing | 31, 32, 33, 37, 38 | Tie | Both correctly ran generated operation sequences against a model and a real system, both caught a sequence-dependent planted bug, both correctly skipped operations whose precondition failed without treating the skip as an error. |
| Cross-value constraints | 34 | Tie | JHusk's `assuming()` and jqwik's `Assume.that()` both correctly rejected values that violated a constraint spanning more than one generated parameter. |
| Edge-case & exhaustive-style coverage | 39, 40, 41, 42, 43 | Tie | Both reliably covered boundary values across many runs; both showed that values strictly between the extremes of a small explicit set aren't individually guaranteed. |
| Multi-feature stress tests | 45, 48, 49, 50 | Tie | Both handled combinations of large generation, custom budgets or constraints, and stateful sequences without conflict. |

## Excluded from scoring

A handful of scenarios don't have a fair jqwik equivalent, or turned out on inspection not to be testing either library at all. Scoring them either way would be comparing two different things and calling it a result.

- **Scenarios 13 and 14** test JHusk's manual builder object, an explicit `Property.forAll(...).check()` call that can be invoked more than once or bound to a variable that's later reassigned. jqwik's API is annotation-driven; there's no equivalent object to construct and invoke this way.
- **Scenario 44** tests JHusk's configurable byte-stream generation budget. jqwik has no equivalent architecture to configure.
- **Scenarios 35 and 36** rely on `Assume.that()` reporting an exhausted property as its own distinct outcome, structurally different from JHusk's thrown `FilterExhaustedException`. Different mechanisms, not a comparable pass or fail.
- **Scenario 46** turned out, on review, to be testing plain JUnit timeout mechanics rather than either library's behavior, and was dropped rather than scored.

## What this comparison doesn't tell us

A few real limits on what can be concluded from this data, stated plainly rather than left implicit.

This suite doesn't test jqwik's production maturity, its Kotlin support, or the breadth of its built-in generator catalog, all real, meaningful differences between the two libraries that simply aren't visible in a 50-scenario adversarial run. It also doesn't test JHusk's central architectural claim under real pressure: that a custom generator built entirely through composition inherits high-quality shrinking with no shrink logic written by hand. Every custom type in this suite was built with `combine`, `filter`, and `flatMap`, exactly the composition tools JHusk's design is built around. A generator written against jqwik's lower-level `Arbitrary` interface directly, with no combinator support, might shrink differently, and this comparison doesn't cover that case.

## Conclusion

Across every scenario where the two libraries' architectures allowed a genuinely fair test, the results were close to identical: the same bugs caught, the same shrinking correctness, the same reproducibility guarantees. One confirmed, real difference turned up (range validation timing), one design tradeoff that isn't a defect on either side (generation budget vs. no bound), and one result that stayed inconclusive rather than being forced into a verdict.

That parity is worth reading honestly rather than triumphantly. It exists in large part because two real gaps found in an earlier comparison, run against JHusk before this suite existed, were fixed first. This suite documents where the two libraries stand today, not a permanent ranking. Both differentiators worth keeping in mind going forward, JHusk's byte-stream shrinking as a structural bet with no shrink logic to hand-write, and jqwik's years of production use, are real, and neither shows up cleanly in a scenario count.
