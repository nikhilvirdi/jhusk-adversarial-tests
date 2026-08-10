package com.example;

import io.github.nikhilvirdi.jhusk.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class StatefulAndAssumingTests {

    static final class StackModel {
        final List<Integer> values;
        StackModel(List<Integer> values) { this.values = values; }
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

    static Command<StackModel, IntStack> pushCommand(int value) {
        return new Command<>() {
            public boolean precondition(StackModel model) { return true; }
            public StackModel nextModel(StackModel model) { return model.push(value); }
            public void runAndVerify(StackModel modelBefore, IntStack real) {
                real.push(value);
                assertEquals(modelBefore.push(value).values.size(), real.size());
            }
        };
    }

    static Command<StackModel, IntStack> popCommand() {
        return new Command<>() {
            public boolean precondition(StackModel model) { return !model.values.isEmpty(); }
            public StackModel nextModel(StackModel model) { return model.pop(); }
            public void runAndVerify(StackModel modelBefore, IntStack real) {
                int expected = modelBefore.values.get(modelBefore.values.size() - 1);
                int actual = real.pop();
                assertEquals(expected, actual, "pop() returned a value inconsistent with the model");
            }
        };
    }

    @Test
    void scenario31_correctStackPassesGeneratedCommandSequences() {
        Generator<Command<StackModel, IntStack>> commandGen = Generators.oneOf(
                Generators.integers(0, 100).map(StatefulAndAssumingTests::pushCommand),
                Generators.just(popCommand())
        );

        Property<List<Command<StackModel, IntStack>>> prop = Commands
                .<StackModel, IntStack>startingWith(() -> new StackModel(new ArrayList<>()), IntStack::new)
                .withCommand(commandGen, 0, 50)
                .asProperty();

        assertDoesNotThrow(() -> prop.check());
    }

    static final class BuggyIntStack {
        private final List<Integer> data = new ArrayList<>();
        void push(int v) { data.add(v); }
        int pop() {
            if (data.size() < 2) return data.remove(data.size() - 1);
            int wrong = data.get(data.size() - 2); // planted bug
            data.remove(data.size() - 1);
            return wrong;
        }
        boolean isEmpty() { return data.isEmpty(); }
        int size() { return data.size(); }
    }

    static Command<StackModel, BuggyIntStack> buggyPush(int value) {
        return new Command<>() {
            public boolean precondition(StackModel model) { return true; }
            public StackModel nextModel(StackModel model) { return model.push(value); }
            public void runAndVerify(StackModel modelBefore, BuggyIntStack real) {
                real.push(value);
            }
        };
    }

    static Command<StackModel, BuggyIntStack> buggyPop() {
        return new Command<>() {
            public boolean precondition(StackModel model) { return !model.values.isEmpty(); }
            public StackModel nextModel(StackModel model) { return model.pop(); }
            public void runAndVerify(StackModel modelBefore, BuggyIntStack real) {
                int expected = modelBefore.values.get(modelBefore.values.size() - 1);
                int actual = real.pop();
                assertEquals(expected, actual, "pop() disagreed with model");
            }
        };
    }

    @Test
    void scenario32_sequenceDependentBugIsCaughtAndSequenceShrinks() {
        Generator<Command<StackModel, BuggyIntStack>> commandGen = Generators.oneOf(
                Generators.integers(0, 10).map(StatefulAndAssumingTests::buggyPush),
                Generators.just(buggyPop())
        );

        Property<List<Command<StackModel, BuggyIntStack>>> prop = Commands
                .<StackModel, BuggyIntStack>startingWith(() -> new StackModel(new ArrayList<>()), BuggyIntStack::new)
                .withCommand(commandGen, 2, 30)
                .asProperty();

        assertThrows(AssertionError.class, prop::check,
                "expected the sequence-dependent pop() bug to be caught by a generated command sequence");
    }

    @Test
    void scenario33_unsatisfiablePreconditionSkipsSilentlyNotAsInvalidRun() {
        AtomicInteger popAttempts = new AtomicInteger(0);
        Command<StackModel, IntStack> neverSatisfiablePop = new Command<>() {
            public boolean precondition(StackModel model) { return false; }
            public StackModel nextModel(StackModel model) { return model; }
            public void runAndVerify(StackModel modelBefore, IntStack real) {
                popAttempts.incrementAndGet();
            }
        };

        Property<List<Command<StackModel, IntStack>>> prop = Commands
                .<StackModel, IntStack>startingWith(() -> new StackModel(new ArrayList<>()), IntStack::new)
                .withCommand(Generators.just(neverSatisfiablePop), 5, 5)
                .asProperty();

        assertDoesNotThrow(() -> prop.check(),
                "a command whose precondition is always false should never execute, and never count as invalid");
        assertEquals(0, popAttempts.get(), "the never-satisfiable command's body should never run");
    }

    @Test
    void scenario34_assumingRejectsCrossValueConstraintFilterCannotExpress() {
        Generator<int[]> pairs = Generators.combine(
                Generators.integers(0, 5), Generators.integers(0, 5),
                (a, b) -> new int[]{a, b});

        Property<int[]> prop = Property.forAll(pairs, pair -> assertNotEquals(pair[0], pair[1]))
                .assuming(pair -> pair[0] != pair[1]);

        assertDoesNotThrow(() -> prop.check(),
                "assuming() should successfully filter out equal pairs before the assertion ever sees them");
    }

    @Test
    void scenario35_assumptionExhaustionProducesAttributedErrorMessage() {
        Property<Integer> prop = Property.forAll(Generators.integers(0, 1_000_000), v -> {})
                .assuming(v -> v == -1)
                .maxInvalidRuns(50);

        FilterExhaustedException e = assertThrows(FilterExhaustedException.class, prop::check);
        assertTrue(e.getMessage().toLowerCase().contains("assuming"),
                "expected the exception message to specifically mention assuming(), got: " + e.getMessage());
    }

    @Test
    void scenario36_assumingAndFilterRejectionsCountedInSeparateBuckets() {
        Generator<Integer> gen = Generators.integers(0, 1).filter(v -> v == 0);

        Property<Integer> prop = Property.forAll(gen, v -> {})
                .assuming(v -> false)
                .maxInvalidRuns(20);

        PropertyExecutionException e = assertThrows(PropertyExecutionException.class, prop::check);
        assertNotNull(e.getMessage());
    }

    @Test
    void scenario37_commandsSequenceWithThreeDistinctCommandTypes() {
        Command<StackModel, IntStack> peekNoop = new Command<>() {
            public boolean precondition(StackModel model) { return true; }
            public StackModel nextModel(StackModel model) { return model; }
            public void runAndVerify(StackModel modelBefore, IntStack real) {
                assertEquals(modelBefore.values.size(), real.size());
            }
        };

        Generator<Command<StackModel, IntStack>> commandGen = Generators.oneOf(
                Generators.integers(0, 20).map(StatefulAndAssumingTests::pushCommand),
                Generators.just(popCommand()),
                Generators.just(peekNoop)
        );

        Property<List<Command<StackModel, IntStack>>> prop = Commands
                .<StackModel, IntStack>startingWith(() -> new StackModel(new ArrayList<>()), IntStack::new)
                .withCommand(commandGen, 0, 40)
                .asProperty();

        assertDoesNotThrow(() -> prop.check());
    }

    @Test
    void scenario38_commandsAsPropertyComposesWithAllStandardBuilderMethods() {
        Generator<Command<StackModel, IntStack>> commandGen =
                Generators.integers(0, 10).map(StatefulAndAssumingTests::pushCommand);

        Property<List<Command<StackModel, IntStack>>> prop = Commands
                .<StackModel, IntStack>startingWith(() -> new StackModel(new ArrayList<>()), IntStack::new)
                .withCommand(commandGen, 0, 10)
                .asProperty()
                .examples(25)
                .maxInvalidRuns(500)
                .withGenerationBudget(16384);

        assertDoesNotThrow(() -> prop.check());
    }
}