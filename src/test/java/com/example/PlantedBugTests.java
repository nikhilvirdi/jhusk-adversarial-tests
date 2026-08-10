package com.example;

import io.github.nikhilvirdi.jhusk.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class PlantedBugTests {

    static int buggyClamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi + 1; // planted bug
        return v;
    }

    @Test
    void scenario16_plantedClampOffByOneIsCaught() {
        assertThrows(AssertionError.class, () ->
                Property.forAll(Generators.integers(-100, 100), v -> {
                    int c = buggyClamp(v, -10, 10);
                    assertTrue(c >= -10 && c <= 10, "clamp(" + v + ") produced out-of-range " + c);
                }).check());
    }

    static <T> List<T> buggyDedup(List<T> in) {
        List<T> out = new ArrayList<>();
        for (T x : in) {
            if (out.isEmpty() || !out.get(out.size() - 1).equals(x)) out.add(x);
        }
        return out;
    }

    @Test
    void scenario17_plantedNonAdjacentDedupBugIsCaught() {
        assertThrows(AssertionError.class, () ->
                Property.forAll(Generators.lists(Generators.integers(0, 3), 1, 8), list -> {
                    List<Integer> deduped = buggyDedup(list);
                    long distinctCount = new HashSet<>(list).size();
                    assertEquals(distinctCount, deduped.size(),
                            "dedup(" + list + ") = " + deduped + " still has non-adjacent duplicates");
                }).check());
    }

    @Test
    void scenario18_largeRedundantListShrinksToMinimalSizeViaChunkDeletion() {
        Generator<List<Integer>> gen = Generators.lists(Generators.integers(0, 100), 0, 80);
        try {
            Property.forAll(gen, list -> {
                assertFalse(list.contains(99), "list should never contain the poison value 99");
            }).check(7L);
            fail("expected the property to eventually falsify given enough examples");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("[99]"),
                    "expected chunk deletion to shrink to the minimal single-element list [99], got: "
                            + e.getMessage());
        }
    }

    static boolean buggyIsPrime(int n) {
        if (n < 2) return true; // planted bug
        for (int i = 2; i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }

    @Test
    void scenario19_plantedIsPrimeMisclassificationIsCaught() {
        assertThrows(AssertionError.class, () ->
                Property.forAll(Generators.integers(0, 1),
                        n -> assertFalse(buggyIsPrime(n), n + " misclassified as prime")).check());
    }

    static <T> Set<T> buggyUnion(Set<T> a, Set<T> b) {
        return a.size() >= b.size() ? new HashSet<>(a) : new HashSet<>(b);
    }

    @Test
    void scenario20_plantedUnionSkipsMergingSmallerSetIsCaught() {
        Generator<Object[]> gen = Generators.combine(
                Generators.sets(Generators.integers(0, 10)),
                Generators.sets(Generators.integers(0, 10)),
                Generators.integers(1000, 2000),
                (setA, setB, marker) -> {
                    Set<Integer> larger = new HashSet<>(setA);
                    Set<Integer> smaller = new HashSet<>(setB);
                    smaller.add(marker);
                    while (larger.size() < smaller.size()) {
                        larger.add(3000 + larger.size());
                    }
                    return new Object[]{larger, smaller};
                });
        assertThrows(AssertionError.class, () ->
                Property.forAll(gen, pair -> {
                    @SuppressWarnings("unchecked") Set<Integer> a = (Set<Integer>) pair[0];
                    @SuppressWarnings("unchecked") Set<Integer> b = (Set<Integer>) pair[1];
                    Set<Integer> union = buggyUnion(a, b);
                    Set<Integer> expected = new HashSet<>(a);
                    expected.addAll(b);
                    assertEquals(expected, union, "union(" + a + "," + b + ") = " + union);
                }).check());
    }
}