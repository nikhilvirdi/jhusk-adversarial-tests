package com.example;

import io.github.nikhilvirdi.jhusk.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class BoundaryAndTypeTests {

    @Test
    void scenario01_integerMinValueSingletonRangeIsReachable() {
        Property.forAll(Generators.integers(Integer.MIN_VALUE, Integer.MIN_VALUE),
                v -> assertEquals(Integer.MIN_VALUE, v)).check();
    }

    @Test
    void scenario02_integerMaxValueSingletonRangeIsReachable() {
        Property.forAll(Generators.integers(Integer.MAX_VALUE, Integer.MAX_VALUE),
                v -> assertEquals(Integer.MAX_VALUE, v)).check();
    }

    @Test
    void scenario03_fullIntRangeNeverEscapesBounds() {
        Property.forAll(Generators.integers(Integer.MIN_VALUE, Integer.MAX_VALUE),
                v -> assertTrue(v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE)).check();
    }

    @Test
    void scenario04_invertedRangeRejectedEagerly() {
        assertThrows(IllegalArgumentException.class, () -> Generators.integers(10, 5));
    }

    @Test
    void scenario05_listExactSizeIsHonored() {
        Property.forAll(Generators.lists(Generators.integers(), 7, 7),
                l -> assertEquals(7, l.size())).check();
    }

    @Test
    void scenario06_listNegativeMinSizeRejectedEagerly() {
        assertThrows(IllegalArgumentException.class,
                () -> Generators.lists(Generators.integers(), -1, 5));
    }

    @Test
    void scenario07_diamondInferenceAcrossChainedMapAndFlatMap() {
        Generator<String> gen = Generators.integers(0, 10)
                .flatMap(i -> Generators.lists(Generators.just(i), 0, i))
                .map(List::size)
                .map(String::valueOf);
        Property.forAll(gen, s -> assertNotNull(s)).check();
    }

    @Test
    void scenario08_combineWithThreeUnrelatedTypesProducingFourthType() {
        Generator<UUID> gen = Generators.combine(
                Generators.integers(),
                Generators.strings(),
                Generators.booleans(),
                (i, s, b) -> new UUID(i.longValue(), b ? 1L : 0L));
        Property.forAll(gen, u -> assertNotNull(u)).check();
    }
}