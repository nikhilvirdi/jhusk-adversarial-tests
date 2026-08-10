package com.example;

import io.github.nikhilvirdi.jhusk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class ImpossibleAndMalformedTests {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void scenario09_impossibleFilterThrowsFilterExhaustedException() {
        Generator<Integer> impossible = Generators.integers(0, 10).filter(i -> i > 1000);
        assertThrows(FilterExhaustedException.class,
                () -> Property.forAll(impossible, i -> {}).check());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void scenario10_compoundImpossibleFilterFailsFast() {
        Generator<Integer> impossible = Generators.integers(0, 1000)
                .filter(i -> i % 97 == 0 && i % 89 == 0 && i > 900);
        assertThrows(FilterExhaustedException.class,
                () -> Property.forAll(impossible, i -> {}).check());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void scenario11_oversizedMandatoryPrefixThrowsGenerationBudgetExceeded() {
        Generator<List<Integer>> gen = Generators.lists(Generators.integers(), 40_000, 40_000);
        assertThrows(GenerationBudgetExceededException.class,
                () -> Property.forAll(gen, l -> {}).check());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void scenario12_mapFunctionThatThrowsSurfacesAsGeneratorCrashException() {
        Generator<Integer> gen = Generators.integers(0, 10).map(i -> {
            if (i == 0) throw new ArithmeticException("divide by zero simulation");
            return 100 / i;
        });
        GeneratorCrashException e = assertThrows(GeneratorCrashException.class,
                () -> Property.forAll(gen, i -> {}).check());
        assertNotNull(e.getCause());
    }

    @Test
    void scenario13_callingCheckTwiceOnSameInstanceRunsBothTimesIndependently() {
        AtomicInteger calls = new AtomicInteger(0);
        Property<Integer> prop = Property.forAll(Generators.integers(0, 100), v -> calls.incrementAndGet());
        assertDoesNotThrow(() -> prop.check());
        int afterFirst = calls.get();
        assertDoesNotThrow(() -> prop.check());
        assertTrue(calls.get() > afterFirst, "second check() call ran zero additional trials");
    }

    @Test
    void scenario14_reassigningGeneratorVariableAfterBindingDoesNotAffectProperty() {
        Generator<Integer> gen = Generators.integers(0, 5);
        Property<Integer> prop = Property.forAll(gen, v -> assertTrue(v >= 0 && v <= 5));
        gen = Generators.integers(1000, 2000);
        assertDoesNotThrow(() -> prop.check(),
                "Property should be bound to the original generator, not a live reference");
    }

    @Test
    void scenario15_mapFunctionReturningNullIsSurfacedToAssertion() {
        Generator<String> gen = Generators.integers(0, 10).map(i -> i == 0 ? null : String.valueOf(i));
        assertThrows(AssertionError.class, () ->
                Property.forAll(gen, s -> assertNotNull(s,
                        "map() producing null should reach the assertion, not be swallowed"))
                        .check());
    }
}