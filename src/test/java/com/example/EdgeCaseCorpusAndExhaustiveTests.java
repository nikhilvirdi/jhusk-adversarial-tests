package com.example;

import io.github.nikhilvirdi.jhusk.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class EdgeCaseCorpusAndExhaustiveTests {

    @Test
    void scenario39_booleanEdgeCaseCorpusGuaranteesBothValuesEveryRun() {
        Set<Boolean> seen = Collections.synchronizedSet(new HashSet<>());
        Property.forAll(Generators.booleans(), seen::add).examples(1).check();
        assertTrue(seen.contains(false) && seen.contains(true),
                "expected the edge-case corpus to guarantee both true and false even with examples(1), saw: " + seen);
    }

    @Test
    void scenario40_boundedIntegerEdgeCaseCorpusGuaranteesMinimumBoundary() {
        AtomicInteger sawMinimum = new AtomicInteger(0);
        Property.forAll(Generators.integers(500, 600), v -> {
            if (v == 500) sawMinimum.incrementAndGet();
        }).examples(1).check();
        assertTrue(sawMinimum.get() > 0,
                "expected the all-zero edge case to guarantee the range minimum is exercised");
    }

    @Test
    void scenario41_exhaustiveGuaranteesFirstAndLastValuesEveryRun() {
        for (long seed = 0; seed < 30; seed++) {
            AtomicInteger sawFirst = new AtomicInteger(0);
            AtomicInteger sawLast = new AtomicInteger(0);
            Property.forAll(Generators.exhaustive("ALPHA", "BETA", "GAMMA", "DELTA", "OMEGA"), v -> {
                if (v.equals("ALPHA")) sawFirst.incrementAndGet();
                if (v.equals("OMEGA")) sawLast.incrementAndGet();
            }).examples(1).check(seed);
            assertTrue(sawFirst.get() > 0, "expected first value ALPHA guaranteed on seed " + seed);
            assertTrue(sawLast.get() > 0, "expected last value OMEGA guaranteed on seed " + seed);
        }
    }

    @Test
    void scenario42_exhaustiveMiddleValuesAreNotIndividuallyGuaranteed() {
        int seedsWhereGammaWasMissedEntirely = 0;
        for (long seed = 0; seed < 40; seed++) {
            AtomicInteger sawGamma = new AtomicInteger(0);
            Property.forAll(Generators.exhaustive("ALPHA", "BETA", "GAMMA", "DELTA", "OMEGA"), v -> {
                if (v.equals("GAMMA")) sawGamma.incrementAndGet();
            }).examples(1).check(seed);
            if (sawGamma.get() == 0) seedsWhereGammaWasMissedEntirely++;
        }
        assertTrue(seedsWhereGammaWasMissedEntirely > 0,
                "expected at least some seeds to miss the middle value GAMMA entirely with examples(1), "
                        + "confirming exhaustive() does not guarantee middle-value coverage -- only first and last are");
    }

    @Test
    void scenario43_customGeneratorCrashOnEdgeCaseBufferIsCorrectlyClassified() {
        Generator<Integer> crashesOnZero = source -> {
            source.startSpan("custom");
            try {
                int v = source.drawInt();
                if (v == 0) throw new IllegalStateException("simulated crash on the all-zero edge case");
                return v;
            } finally {
                source.endSpan();
            }
        };
        assertThrows(GeneratorCrashException.class,
                () -> Property.forAll(crashesOnZero, v -> {}).check());
    }

    @Test
    void scenario44_edgeCaseCorpusBuffersRespectCustomGenerationBudget() {
        Property<Integer> prop = Property.forAll(Generators.integers(0, 100), v -> {})
                .withGenerationBudget(16)
                .examples(5);
        assertDoesNotThrow(() -> prop.check(),
                "a small generator's edge cases should succeed even under a tiny custom budget");
    }
}