package cc.nimbusnova.mc.lesson;

public final class LessonModelTest {
    private LessonModelTest() {
        /* w */
    }

    public static void main(final String[] args) {
        final LessonModel model = new LessonModel();
        assert !model.adjustPressure(-1) : "Unsealed chamber cannot change pressure";
        assert !model.canCycle();
        model.isolated = true;
        assert !model.canCycle();
        assert model.adjustPressure(-1);
        assert !model.canCycle() : "Pressure mismatch must block cycle";
        for (int step = 0; step < 4; step++)
            assert model.adjustPressure(-1);
        assert model.pressure == 0 && model.canCycle();
        assert !model.adjustPressure(-1) : "Pressure must not underflow";
        for (int step = 0; step < 5; step++)
            assert model.adjustPressure(1);
        assert model.pressure == 100 && !model.adjustPressure(1);
        assert LessonModel.detectorIndex(0) == 100 && LessonModel.mass(0) == 0;
        model.changeLayer(0);
        assert model.layers == 1 && LessonModel.detectorIndex(model.layers) == 90;
        model.changeLayer(0);
        assert LessonModel.detectorIndex(model.layers) == 70 && LessonModel.mass(model.layers) == 3;
        model.changeLayer(0);
        assert LessonModel.detectorIndex(model.layers) == 55 && LessonModel.mass(model.layers) == 5;
        model.changeLayer(0);
        assert model.layers == 0;
        model.changeLayer(1);
        assert model.layers == 4 : "Slots must remain independent";
        System.out.println("Lesson models: pressure interlocks, bounds and shielding tradeoffs passed");
    }
}
