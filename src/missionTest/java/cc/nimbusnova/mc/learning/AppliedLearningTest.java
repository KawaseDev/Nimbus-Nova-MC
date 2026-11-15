package cc.nimbusnova.mc.learning;

import java.util.Map;

public final class AppliedLearningTest {
    private AppliedLearningTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final Map<AppliedLearning.Case, Integer> supported = Map.of(
                AppliedLearning.Case.SOLAR, 1, AppliedLearning.Case.RELAY, 2,
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
        for (final int[] readings : new int[][] { { 110, 60, 0 }, { 110, 125, 1 }, { 110, 110, 2 } }) {
            final AppliedLearning dust = new AppliedLearning(AppliedLearning.Case.DUST, readings[0], readings[1]);
            if (!dust.answer((readings[2] + 1) % 3) || dust.solved())
                throw new AssertionError("Dust prediction must reject a trend opposite to the live power balance");
            if (!dust.feedback().contains("Supply " + readings[0] + " minus load " + readings[1])
                    || !dust.feedback().contains("gives " + (readings[0] - readings[1])))
                throw new AssertionError("Dust feedback must explain the actual displayed readings");
            if (!dust.answer(readings[2]) || !dust.solved())
                throw new AssertionError("Dust charge/drain/steady answer must follow the live readings");
        }
        System.out.println("Applied learning checks passed: eight equipment predictions, three live power balances, evidence feedback and immutable completion.");
    }
}
