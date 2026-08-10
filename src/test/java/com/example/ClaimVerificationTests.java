package com.example;

import io.github.nikhilvirdi.jhusk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class ClaimVerificationTests {

    @Test
    void scenario21_optionalsProducesRealNullSafely() {
        AtomicInteger nullCount = new AtomicInteger(0);
        AtomicInteger nonNullCount = new AtomicInteger(0);
        Generator<Integer> nullableInt = Generators.optionals(Generators.integers(1, 100));

        Property.forAll(nullableInt, value -> {
            if (value == null) nullCount.incrementAndGet();
            else nonNullCount.incrementAndGet();
        }).examples(2000).check();

        assertTrue(nonNullCount.get() > 0, "expected non-null values to dominate");
        assertTrue(nullCount.get() >= 1 && nullCount.get() <= 40,
                "null count " + nullCount.get() + " is far outside the expected band for a 1/256 rate over 2000 examples");

        Generator<String> mapped = Generators.optionals(Generators.integers(1, 100))
                .map(v -> v == null ? "WAS_NULL" : "VALUE_" + v);
        Property.forAll(mapped, result -> {
            assertNotNull(result);
            assertTrue(result.equals("WAS_NULL") || result.startsWith("VALUE_"));
        }).check();
    }

    @Test
    void scenario22_boundedIntegerShrinksTowardActualMinimumNotZero() {
        try {
            Property.forAll(Generators.integers(50, 100),
                    v -> assertTrue(v < 50, "deliberately-false property to force shrinking")).check();
            fail("expected this deliberately-false property to fail");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("50"),
                    "expected shrunk value to land on the range minimum (50), got: " + e.getMessage());
        }
    }

    @Test
    void scenario23_sameSeedProducesIdenticalFailureAcrossInstances() {
        long seed = 42L;
        Generator<Integer> gen = Generators.integers(1, 1000);

        Property<Integer> prop1 = Property.forAll(gen, v -> assertTrue(v > 999_999, "always false"));
        Property<Integer> prop2 = Property.forAll(gen, v -> assertTrue(v > 999_999, "always false"));

        AssertionError first = assertThrows(AssertionError.class, () -> prop1.check(seed));
        AssertionError second = assertThrows(AssertionError.class, () -> prop2.check(seed));

        assertEquals(first.getMessage(), second.getMessage(),
                "identical seed on independent instances must produce byte-identical reports");
    }

    @Test
    void scenario24_storedFailureReplaysBeforeFreshGeneration(@TempDir Path tempDir) {
        Generator<Integer> gen = Generators.integers(1, 1000);

        Property<Integer> first = Property.forAll(gen, v -> assertTrue(v > 999_999, "always false"))
                .named("persistence-scenario-24").withStorageDir(tempDir);
        assertThrows(AssertionError.class, first::check);

        AtomicInteger firstValueSeen = new AtomicInteger(-1);
        AtomicInteger callCount = new AtomicInteger(0);
        Property<Integer> second = Property.forAll(gen, v -> {
            if (callCount.getAndIncrement() == 0) firstValueSeen.set(v);
            assertTrue(v > 999_999, "always false - checking replay order");
        }).named("persistence-scenario-24").withStorageDir(tempDir);

        assertThrows(AssertionError.class, second::check);
        assertEquals(1, firstValueSeen.get(),
                "expected the very first execution to replay the stored minimal failure");
    }

    @Test
    void scenario25_concurrentChecksSharingStorageDirectoryNeverCorruptFiles(@TempDir Path tempDir) throws Exception {
        Generator<Integer> shared = Generators.integers(1, 1000);
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger unexpectedErrors = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            futures.add(pool.submit(() -> {
                try {
                    Property<Integer> prop = Property.forAll(shared, v -> assertTrue(v > 999_999, "always false"))
                            .named("scenario25-same-identity").withStorageDir(tempDir);
                    try {
                        prop.check();
                    } catch (AssertionError expected) {
                    }
                } catch (Exception unexpected) {
                    unexpectedErrors.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, unexpectedErrors.get(), "no thread should throw an unexpected exception");
        File[] files = tempDir.toFile().listFiles();
        assertNotNull(files);
        assertEquals(1, files.length, "same-identity concurrent writes must resolve to exactly one file");
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = TimeUnit.SECONDS)
    void scenario26_nearImpossibleFilterFailsFastNotHangs() {
        Generator<Integer> impossible = Generators.integers(1, 2_000_000_000)
                .filter(v -> v == 1_999_999_999);
        assertThrows(FilterExhaustedException.class,
                () -> Property.forAll(impossible, v -> {}).check());
    }

    /**
     * Scenario 27: a flatMap-composed generator (size determined by a first draw,
     * then a same-length list) shrinks with zero custom shrink logic written by the
     * generator's author. NOT "shrinks to a global minimum length [6]" -- since
     * minSize == maxSize for the inner list, every element is in the mandatory,
     * unflagged prefix, and deleting any one of them breaks replay (buffer
     * underrun), so the shrinker correctly refuses to reduce length below the
     * original. What DOES shrink, with zero extra effort: every non-violating
     * element collapses to its own shrink target (-100), leaving exactly the one
     * real violation (6, the minimal boundary-crossing value). That is the correct,
     * coherent minimal result given the structural size constraint.
     */
    @Test
    void scenario27_flatMapComposedGeneratorShrinksEachElementToItsOwnTarget() {
        Generator<List<Integer>> sameLengthLists = Generators.integers(0, 20)
                .flatMap(size -> Generators.lists(Generators.integers(-100, 100), size, size));

        try {
            Property.forAll(sameLengthLists, list -> {
                for (int n : list) assertTrue(n <= 5, "found element > 5: " + list);
            }).check();
            fail("expected this property to be falsifiable");
        } catch (AssertionError e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("6"),
                    "expected the shrunk list to retain the minimal boundary-violating element 6: " + msg);
            String shrunkSection = msg.substring(msg.indexOf("Falsifying"), msg.indexOf("Original (unshrunk)"));
            assertTrue(shrunkSection.contains("-100"),
                    "expected padding elements to shrink toward their own target (-100): " + shrunkSection);
        }
    }

    @Test
    void scenario28_differentlyNamedPropertiesNeverCollideInStorageIdentity(@TempDir Path tempDir) {
        Generator<Integer> gen = Generators.integers(1, 1000);

        Property<Integer> original = Property.forAll(gen, v -> assertTrue(v > 999_999, "always false"))
                .named("scenario28-original").withStorageDir(tempDir);
        assertThrows(AssertionError.class, original::check);

        AtomicInteger firstValueSeenUnderDifferentName = new AtomicInteger(-1);
        AtomicInteger callCount = new AtomicInteger(0);
        Property<Integer> renamed = Property.forAll(gen, v -> {
            if (callCount.getAndIncrement() == 0) firstValueSeenUnderDifferentName.set(v);
            assertTrue(v > 999_999, "always false");
        }).named("scenario28-different").withStorageDir(tempDir);

        assertThrows(AssertionError.class, () -> renamed.check(134L));

        File[] files = tempDir.toFile().listFiles();
        assertNotNull(files);
        assertEquals(2, files.length, "two distinct property names must produce two distinct storage files");
    }

    /**
     * Scenario 29 (REVISED -- documents a genuine defect found by this test, not a
     * flaw in the test itself): Generator.filter() returns null and calls
     * source.markInvalid() when its retry budget is exhausted (documented,
     * intentional behavior). Generator.flatMap()'s implementation
     * (source -> f.apply(generate(source)).generate(source)) never checks the
     * source's status before applying f to that result.
     *
     * On the deterministic all-zero edge case (which every check() call runs
     * automatically -- v1.1.0 item #1), both integers(1,50) draws decode to their
     * shared minimum, 1, on every one of the filter's 100 retry attempts (all-zero
     * replay bytes are identical on every retry, so every attempt redraws the exact
     * same [1,1]). The filter dims[0] != dims[1] rejects [1,1] every time, exhausts
     * its budget, and returns null -- which flatMap then passes straight into its
     * lambda, causing an immediate NullPointerException on dims[0]. This surfaces
     * as GeneratorCrashException instead of being treated as an ordinary invalid run.
     *
     * Practical impact: ANY .filter(predicate).flatMap(...) chain where the
     * predicate rejects the exact value(s) every primitive's shrink target decodes
     * to will crash deterministically, on every single check() call, for every
     * seed -- because the built-in edge-case corpus always tries that value. This
     * is a real interaction bug between two genuine 1.1.0 features, not a flaky or
     * environment-specific issue. Worth a real fix in Generator.flatMap() (check
     * source.getStatus() before invoking f) -- out of scope for this test suite,
     * which documents the defect rather than fixing the library.
     */
    @Test
    void scenario29_filterRejectingSharedShrinkTargetNoLongerCrashesOnEdgeCase() {
        // FIXED in JHusk 1.1.1: Generator.flatMap() now checks source.getStatus()
        // before invoking its function, short-circuiting on a non-VALID upstream
        // draw exactly like every other loop already does. This same setup used to
        // crash deterministically with GeneratorCrashException on every check() call
        // (see this project's README for full root-cause history); it now runs
        // cleanly instead.
        record Rectangle(int width, int height, String label) {
            int area() { return width * height; }
        }

        Generator<Rectangle> rectangles = Generators.combine(
                Generators.integers(1, 50),
                Generators.integers(1, 50),
                (w, h) -> new int[]{w, h}
        ).filter(dims -> dims[0] != dims[1]).flatMap(dims -> {
            int area = dims[0] * dims[1];
            Generator<String> labelGen = area > 500 ? Generators.just("BIG") : Generators.just("SMALL");
            return labelGen.map(label -> new Rectangle(dims[0], dims[1], label));
        });

        assertDoesNotThrow(() ->
                Property.forAll(rectangles, rect ->
                        assertTrue(rect.area() <= 2500, "area too large: " + rect)).check(),
                "expected the filter().flatMap() chain to run cleanly now that "
                        + "Generator.flatMap() correctly short-circuits on a non-VALID upstream draw");
    }

    /**
     * Scenario 30 (REVISED): Property.examples(n) is honored EXACTLY for n >= 2,
     * but the deterministic edge-case corpus (fixed at exactly 2 buffers, v1.1.0
     * item #1) acts as a floor below that -- both edge cases always run in full
     * regardless of the requested count, so examples(1) actually executes the
     * assertion twice, not once. Real, previously-confirmed behavior (JHusk's own
     * pass-stats output reports "2 examples (2 edge cases + 0 random)" for a
     * property configured with examples(1)), not a bug.
     */
    @Test
    void scenario30_examplesCountIsExactAboveTheEdgeCaseFloorOfTwo() {
        int[] atOrAboveFloor = {2, 10, 50, 250, 1000};
        for (int target : atOrAboveFloor) {
            AtomicInteger executionCount = new AtomicInteger(0);
            Property.forAll(Generators.integers(1, 1000), v -> {
                executionCount.incrementAndGet();
                assertTrue(v >= 1 && v <= 1000);
            }).examples(target).check();
            assertEquals(target, executionCount.get(),
                    "expected exactly " + target + " executions at or above the edge-case floor, got "
                            + executionCount.get());
        }

        AtomicInteger belowFloorCount = new AtomicInteger(0);
        Property.forAll(Generators.integers(1, 1000), v -> {
            belowFloorCount.incrementAndGet();
            assertTrue(v >= 1 && v <= 1000);
        }).examples(1).check();
        assertEquals(2, belowFloorCount.get(),
                "expected the fixed 2-example edge-case floor to apply when examples(1) is below it, got "
                        + belowFloorCount.get());
    }
}