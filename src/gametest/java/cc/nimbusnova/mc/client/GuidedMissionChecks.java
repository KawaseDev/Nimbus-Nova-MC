package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.npc.MissionAstronaut;
import cc.nimbusnova.mc.npc.MissionNpcs;
import cc.nimbusnova.mc.client.npc.NpcDialogueScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class GuidedMissionChecks {
    private GuidedMissionChecks() {
        /* w */
    }

    static void prepare(final ClientGameTestContext context, final TestSingleplayerContext world) {
        context.setScreen(() -> null);
        world.getServer().runCommand("mission start");
        if (world.getServer().computeOnServer(server -> MissionController.get(server).snapshot().phase()) != MissionState.Phase.READY)
            throw new AssertionError("Hazards started before the crew was prepared");

        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -3.5 66 -6.5 0 0");
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final var guide = player.level().getEntitiesOfClass(MissionAstronaut.class, new AABB(-8, 65, -8, 8, 70, 8)).stream()
                    .filter(npc -> npc.getType() == MissionNpcs.GUIDE).findFirst().orElseThrow();
            guide.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        });
        context.waitForScreen(NpcDialogueScreen.class);
        context.takeScreenshot("guided-briefing");
        context.setScreen(() -> null);
        repair(context, world, true, true);
        repair(context, world, false, false);
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 40 66 -19 0 0");
        use(world, new BlockPos(40, 66, -16));
        context.waitFor(client -> GuidedMissionClient.progress() != null && GuidedMissionClient.progress().stage().equals("DELIVER"));
        context.takeScreenshot("guided-sample-return");
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 6 66 -2 0 0");
        use(world, new BlockPos(6, 66, 1));
        context.waitFor(client -> GuidedMissionClient.progress() != null && GuidedMissionClient.progress().ready());
        if (NimbusNovaClient.snapshot.state().elapsedSeconds() != 0)
            throw new AssertionError("The timer advanced during preparation");
        context.takeScreenshot("guided-crew-ready");
    }

    static void repair(final ClientGameTestContext context, final TestSingleplayerContext world, final boolean power, final boolean tryWrongAction) {
        final BlockPos position = power ? new BlockPos(-24, 66, 0) : new BlockPos(-5, 66, 3);
        world.getServer().runCommand(power ? "execute in nimbus_nova:mars_training run tp @p -24 66 -3 0 20"
                : "execute in nimbus_nova:mars_training run tp @p -5 66 0 0 20");
        use(world, position);
        context.waitForScreen(TaskScreen.class);
        context.takeScreenshot(power ? "guided-power-task" : "guided-oxygen-task");
        if (tryWrongAction)
            choose(context, "Reconnect");

        for (final String action : power ? new String[] { "Isolate supply", "Replace fuse", "Reconnect" }
                : new String[] { "Seal valve", "Read pressure", "Start recycler" })
            choose(context, action);

        context.takeScreenshot(power ? "guided-power-complete" : "guided-oxygen-complete");
        context.clickScreenButton("Next task");
        context.waitForScreen(null);
    }

    private static void choose(final ClientGameTestContext context, final String action) {
        final Screen previous = context.computeOnClient(client -> client.gui.screen());
        context.clickScreenButton(action);
        context.waitFor(client -> client.gui.screen() instanceof TaskScreen && client.gui.screen() != previous);
    }

    static void use(final TestSingleplayerContext world, final BlockPos position) {
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        });
        world.getConnection().waitForClientboundPackets();
    }

    static void advanceTo(final TestSingleplayerContext world, final int targetSecond) {
        world.getServer().runOnServer(server -> {
            final MissionController controller = MissionController.get(server);
            for (int tick = 0; tick < MissionState.DURATION_SECONDS * 20 + 20
                    && controller.snapshot().elapsedSeconds() < targetSecond && controller.snapshot().phase() == MissionState.Phase.ACTIVE; tick++)
                ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);

            if (controller.snapshot().elapsedSeconds() < targetSecond)
                throw new AssertionError("Mission ended before the expected stage: " + controller.snapshot().lastEvent());
        });
    }
}
