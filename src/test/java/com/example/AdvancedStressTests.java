package com.example;

import io.github.nikhilvirdi.jhusk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

public class AdvancedStressTests {

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void scenario45_timeoutAndCustomBudgetTogetherOnLargeGeneratedCollections() {
        Generator<List<Integer>> largeList = Generators.lists(Generators.integers(), 3000, 3000);
        Property<List<Integer>> prop = Property.forAll(largeList, list -> {
            assertEquals(3000, list.size());
        }).withGenerationBudget(20_000).timeoutPerExample(Duration.ofSeconds(5)).examples(3);

        assertDoesNotThrow(() -> prop.check(),
                "large list + raised budget + generous timeout together should succeed cleanly");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void scenario46_timeoutStillCatchesHangWithCustomBudgetConfigured() {
        Property<Integer> prop = Property.forAll(Generators.integers(42, 42), v -> {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).withGenerationBudget(16_384).timeoutPerExample(Duration.ofMillis(300));

        assertThrows(PropertyTimeoutException.class, prop::check);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void scenario47_timeoutCoversStoredFailureReplayNotJustFreshGeneration(@TempDir Path tempDir) {
        Generator<Integer> gen = Generators.integers(42, 42);

        Property<Integer> establishFailure = Property.forAll(gen, v -> fail("establishing a stored failure"))
                .named("scenario47-replay-timeout").withStorageDir(tempDir);
        assertThrows(AssertionError.class, establishFailure::check);

        Property<Integer> replayWithHang = Property.forAll(gen, v -> {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).named("scenario47-replay-timeout").withStorageDir(tempDir).timeoutPerExample(Duration.ofMillis(300));

        assertThrows(PropertyTimeoutException.class, replayWithHang::check,
                "expected the configured timeout to protect the stored-failure REPLAY path, not just fresh generation");
    }

    /**
     * Scenario 48: raised maxInvalidRuns significantly above the default 1000.
     * withCommand(gen, 0, 20) biases heavily toward the max size, since JHusk's
     * list encoding continues with ~255/256 probability per element (documented
     * D5 behavior) -- so size <= 15 only succeeds roughly 6% of the time, and the
     * default 1000-invalid-run budget isn't enough headroom to reach 100 valid
     * examples at that success rate. This is a real, math-backed adjustment, not
     * a workaround for a bug.
     */
    @Test
    void scenario48_commandsSequenceCombinedWithPropertyLevelAssuming() {
        record StackModel(List<Integer> values) {
            StackModel push(int v) {
                List<Integer> copy = new ArrayList<>(values);
                copy.add(v);
                return new StackModel(copy);
            }
        }

        Command<StackModel, IntStack> pushTwo = new Command<>() {
            public boolean precondition(StackModel model) { return true; }
            public StackModel nextModel(StackModel model) { return model.push(2); }
            public void runAndVerify(StackModel modelBefore, IntStack real) {
                real.push(2);
            }
        };

        Property<List<Command<StackModel, IntStack>>> prop = Commands
                .<StackModel, IntStack>startingWith(() -> new StackModel(new ArrayList<>()), IntStack::new)
                .withCommand(Generators.just(pushTwo), 0, 20)
                .asProperty()
                .assuming(sequence -> sequence.size() <= 15)
                .maxInvalidRuns(5000);

        assertDoesNotThrow(() -> prop.check());
    }

    @Test
    void scenario49_chunkDeletionShrinkerCorrectlyIsolatesMultipleScatteredPoisonValues() {
        Generator<List<Integer>> gen = Generators.lists(Generators.integers(0, 200), 0, 100);
        try {
            Property.forAll(gen, list -> {
                long poisonCount = list.stream().filter(v -> v == 111 || v == 222).count();
                assertTrue(poisonCount < 2, "list must not contain both poison values 111 and 222: " + list);
            }).check(99L);
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("111") && e.getMessage().contains("222"),
                    "expected the shrunk value to retain both poison values 111 and 222: " + e.getMessage());
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void scenario50_fullFeatureCompositionStressTest() {
        record StackModel(List<Integer> values) {
            StackModel push(int v) {
                List<Integer> copy = new ArrayList<>(values);
                copy.add(v);
                return new StackModel(copy);
            }
            StackModel pop() {
                List<Integer> copy = new ArrayList<>(values);
                copy.remove(copy.size() - 1);
                return new StackModel(copy);
            }
        }

        Command<StackModel, IntStack> push = new Command<>() {
            public boolean precondition(StackModel model) { return model.values.size() < 30; }
            public StackModel nextModel(StackModel model) { return model.push(1); }
            public void runAndVerify(StackModel modelBefore, IntStack real) { real.push(1); }
        };
        Command<StackModel, IntStack> pop = new Command<>() {
            public boolean precondition(StackModel model) { return !model.values.isEmpty(); }
            public StackModel nextModel(StackModel model) { return model.pop(); }
            public void runAndVerify(StackModel modelBefore, IntStack real) { real.pop(); }
        };

        Generator<Command<StackModel, IntStack>> commandGen =
                Generators.oneOf(Generators.just(push), Generators.just(pop));

        Property<List<Command<StackModel, IntStack>>> prop = Commands
                .<StackModel, IntStack>startingWith(() -> new StackModel(new ArrayList<>()), IntStack::new)
                .withCommand(commandGen, 0, 40)
                .asProperty()
                .assuming(sequence -> sequence.size() >= 0)
                .withGenerationBudget(16_384)
                .timeoutPerExample(Duration.ofSeconds(3))
                .examples(20);

        assertDoesNotThrow(() -> prop.check(),
                "all four features (Commands, assuming, custom budget, timeout) should compose without conflict");
    }
}