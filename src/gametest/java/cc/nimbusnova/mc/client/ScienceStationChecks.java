package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.lesson.ScienceStationScreen;
import cc.nimbusnova.mc.lesson.LessonPayload;
import cc.nimbusnova.mc.lesson.LessonModel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

final class ScienceStationChecks {
    private ScienceStationChecks() {
        /* w */
    }

    static void practice(final ClientGameTestContext context, final TestSingleplayerContext world) {
        open(context, world, new BlockPos(-1, 66, -10));
        action(context, 3);
        if (state(context).target() != 0)
            throw new AssertionError("Unsafe pressure cycle was accepted");
        action(context, 0);
        for (int step = 0; step < 5; step++)
            action(context, 1);

        GuidedMissionChecks.capture(context, "lesson-airlock-equalized");
        action(context, 3);
        if (state(context).target() != 100 || state(context).isolated())
            throw new AssertionError("Equalized practice cycle did not complete");
        context.setScreen(() -> null);
        open(context, world, new BlockPos(2, 66, 25));
        action(context, 0);
        action(context, 1);
        action(context, 1);
        if (LessonModel.detectorIndex(state(context).layers()) >= 100 || LessonModel.mass(state(context).layers()) <= 0)
            throw new AssertionError("Shield layers must trade mass for lower detector index");
        GuidedMissionChecks.capture(context, "lesson-shielding");
        context.setScreen(() -> null);
        open(context, world, new BlockPos(3, 66, 5));
        action(context, 0);
        if (state(context).cells() != 2)
            throw new AssertionError("Rehearsal spent a real backup cell");
        GuidedMissionChecks.capture(context, "lesson-backup-practice");
        context.setScreen(() -> null);
    }

    static void active(final ClientGameTestContext context, final TestSingleplayerContext world) {
        open(context, world, new BlockPos(3, 66, 5));
        action(context, 0);
        if (state(context).cells() != 1)
            throw new AssertionError("Active reserve transfer must consume exactly one shared cell");
        GuidedMissionChecks.capture(context, "lesson-backup-active");
        context.setScreen(() -> null);
        open(context, world, new BlockPos(-1, 66, -10));
        action(context, 0);
        for (int step = 0; step < 5; step++)
            action(context, 2);

        action(context, 3);
        if (state(context).target() != 0 || !state(context).feedback().contains("2 shared oxygen"))
            throw new AssertionError("Active airlock cycle did not apply its resource consequence");
        context.setScreen(() -> null);
        System.out.println("Science station runtime checks passed: pressure interlock, rehearsal, finite backup power and shielding comparison.");
    }

    private static void open(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos position) {
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p " + position.getX() + " 66 " + (position.getZ() - 3) + " 0 15");
        GuidedMissionChecks.use(world, position);
        context.waitForScreen(ScienceStationScreen.class);
    }

    private static LessonPayload state(final ClientGameTestContext context) {
        return context.computeOnClient(client -> ((ScienceStationScreen) client.gui.screen()).state());
    }

    private static void action(final ClientGameTestContext context, final int action) {
        final int revision = state(context).revision();
        context.runOnClient(client -> ((ScienceStationScreen) client.gui.screen()).sendAction(action));
        context.waitFor(client -> client.gui.screen() instanceof ScienceStationScreen screen && screen.state().revision() != revision);
    }
}
