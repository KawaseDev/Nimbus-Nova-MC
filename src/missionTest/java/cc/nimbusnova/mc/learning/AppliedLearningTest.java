package cc.nimbusnova.mc.learning;

import java.util.Map;

public final class AppliedLearningTest {
    private AppliedLearningTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final Map<AppliedLearning.Case, Integer> supported = Map.of(
                AppliedLearning.Case.SOLAR, 1, AppliedLearning.Case.DUST, 1, AppliedLearning.Case.RELAY, 2,
                AppliedLearning.Case.DOCKING, 1, AppliedLearning.Case.DESCENT, 2, AppliedLearning.Case.EMERGENCY, 0,
                AppliedLearning.Case.FILTER, 2, AppliedLearning.Case.RESTART, 0, AppliedLearning.Case.SURFACE, 1);
        for (final var entry : supported.entrySet()) {
            final AppliedLearning model = new AppliedLearning(entry.getKey());
            if (model.answer(-1) || model.answer(3) || model.solved())
                throw new AssertionError("Out-of-range answers must not satisfy a crew check");
            if (!model.answer((entry.getValue() + 1) % 3) || model.solved()
                    || !model.feedback().startsWith("That action") || !model.feedback().contains(entry.getKey().explanation))
                throw new AssertionError("A wrong prediction must explain the equipment consequence without completing");
            if (!model.answer(entry.getValue()) || !model.solved() || !model.feedback().startsWith("Prediction supported"))
                throw new AssertionError("Supported equipment evidence must complete the check");
            final String complete = model.feedback();
            if (model.answer(entry.getValue()) || !model.feedback().equals(complete))
                throw new AssertionError("A completed check must be immutable rather than provide repeat rewards");
        }
        System.out.println("Applied learning checks passed: nine incident predictions, evidence feedback and immutable completion.");
    }
}
