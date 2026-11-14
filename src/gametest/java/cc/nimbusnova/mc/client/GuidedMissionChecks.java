package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.mission.MissionPreparation;
import cc.nimbusnova.mc.mission.MissionTaskOpenPayload;
import cc.nimbusnova.mc.npc.MissionAstronaut;
import cc.nimbusnova.mc.npc.MissionNpcs;
import cc.nimbusnova.mc.content.OutpostContent;
import cc.nimbusnova.mc.client.npc.NpcDialogueScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
        GuidedMissionChecks.capture(context, "guided-briefing");
        context.setScreen(() -> null);
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 7 66 -6 0 0");
        use(world, new BlockPos(7, 66, -3));
        context.waitFor(client -> GuidedMissionClient.progress() != null && GuidedMissionClient.progress().stage().equals("POWER"));
        repair(context, world, true, true);
        repair(context, world, false, false);
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 40 66 -19 0 0");
        use(world, new BlockPos(40, 66, -16));
        context.waitFor(client -> GuidedMissionClient.progress() != null && GuidedMissionClient.progress().stage().equals("DELIVER"));
        GuidedMissionChecks.capture(context, "guided-sample-return");
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 6 66 -2 0 0");
        use(world, new BlockPos(6, 66, 1));
        context.waitFor(client -> GuidedMissionClient.progress() != null && GuidedMissionClient.progress().ready());
        if (NimbusNovaClient.snapshot.state().elapsedSeconds() != 0)
            throw new AssertionError("The timer advanced during preparation");
        GuidedMissionChecks.capture(context, "guided-crew-ready");
    }

    static void checkOutpost(final ClientGameTestContext context, final TestSingleplayerContext world) {
        final BlockPos sample = new BlockPos(40, 66, -16), playerBuild = new BlockPos(12, 66, -16);
        world.getServer().runOnServer(server -> {
            final var level = world.getConnection().getServerLevel();
            level.setBlock(sample, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(playerBuild, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        });
        world.getServer().runCommand("mars upgrade");
        world.getServer().runOnServer(server -> {
            final var level = world.getConnection().getServerLevel();
            if (!level.getBlockState(sample).is(Blocks.DIRT))
                throw new AssertionError("Outpost upgrade replaced an unknown player block");
            level.setBlock(sample, OutpostContent.SAMPLE_DEPOSIT.defaultBlockState(), Block.UPDATE_CLIENTS);
        });
        world.getServer().runCommand("mars upgrade");
        world.getServer().runCommand("mars upgrade");
        world.getServer().runOnServer(server -> {
            final var level = world.getConnection().getServerLevel();
            if (!level.getBlockState(playerBuild).is(Blocks.GOLD_BLOCK))
                throw new AssertionError("Outpost upgrade changed an unrelated build");
            final BlockPos door = new BlockPos(0, 66, -12);
            if (!(level.getBlockState(door).getBlock() instanceof DoorBlock)
                    || level.getBlockState(door).getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                    || level.getBlockState(door.above()).getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER)
                throw new AssertionError("The airlock door is missing a valid half");
        });
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 0.5 66 -12.5 0 0");
        context.waitFor(client -> client.level != null && client.level.getBlockState(new BlockPos(0, 66, -12)).getValue(DoorBlock.OPEN));
        capture(context, "laser-door-open");
        world.getServer().runOnServer(server -> {
            final var level = world.getConnection().getServerLevel();
            for (int x = -1; x <= 0; x++) {
                if (!level.getBlockState(new BlockPos(x, 66, -12)).getCollisionShape(level, new BlockPos(x, 66, -12)).isEmpty())
                    throw new AssertionError("An inactive laser field blocked the doorway");
                for (final int z : new int[] { -13, -11 }) {
                    if (level.getBlockState(new BlockPos(x, 66, z)).is(Blocks.STONE_PRESSURE_PLATE))
                        throw new AssertionError("Laser door still depends on a pressure plate");
                }
            }
        });
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 0.5 66 -17 0 0");
        context.waitFor(client -> client.level != null && !client.level.getBlockState(new BlockPos(0, 66, -12)).getValue(DoorBlock.OPEN));
        context.waitTicks(5);
        capture(context, "laser-door-closed");
    }

    static void repair(final ClientGameTestContext context, final TestSingleplayerContext world, final boolean power, final boolean tryWrongAction) {
        final BlockPos position = power ? new BlockPos(-24, 66, 0) : new BlockPos(-5, 66, 3);
        world.getServer().runCommand(power ? "execute in nimbus_nova:mars_training run tp @p -24 66 -3 0 20"
                : "execute in nimbus_nova:mars_training run tp @p -5 66 0 0 20");
        use(world, position);
        context.waitForScreen(TaskScreen.class);
        GuidedMissionChecks.capture(context, power ? "guided-power-task" : "guided-oxygen-task");
        if (power) {
            if (tryWrongAction)
                choose(context, "Rotate tile 9");

            for (int cell = 0; cell < 9 && !task(context).complete(); cell++) {
                int turns = 0;
                while (!task(context).complete() && (task(context).puzzleState() >> cell * 2 & 3) != 0 && turns++ < 4)
                    choose(context, "Rotate tile " + (cell + 1));
            }
        } else {
            final String[] valves = { "intake", "pump", "vent" };
            final int[] target = { 2, 2, 0 };
            for (int valve = 0; valve < valves.length; valve++) {
                int turns = 0;
                while (MissionPreparation.oxygenValve(task(context).puzzleState(), valve) != target[valve] && turns++ < 5)
                    choose(context, (MissionPreparation.oxygenValve(task(context).puzzleState(), valve) < target[valve]
                            ? "Increase " : "Decrease ") + valves[valve]);
            }

            world.getServer().runOnServer(server -> {
                for (int tick = 0; tick < MissionPreparation.OXYGEN_STABLE_TICKS; tick++)
                    ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
            });
            context.waitFor(client -> client.gui.screen() instanceof TaskScreen screen
                    && screen.serverState().aux() >>> 16 >= MissionPreparation.OXYGEN_STABLE_TICKS);
            choose(context, "Start recycler");
        }

        if (!task(context).complete())
            throw new AssertionError("The visual equipment puzzle did not complete");

        GuidedMissionChecks.capture(context, power ? "guided-power-complete" : "guided-oxygen-complete");
        context.clickScreenButton("Next task");
        context.waitForScreen(null);
    }

    private static MissionTaskOpenPayload task(final ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            if (!(client.gui.screen() instanceof TaskScreen screen))
                throw new AssertionError("Expected an open equipment puzzle");
            return screen.serverState();
        });
    }

    private static void choose(final ClientGameTestContext context, final String action) {
        final int previous = task(context).step();
        context.clickScreenButton(action);
        context.waitFor(client -> client.gui.screen() instanceof TaskScreen screen
                && (screen.serverState().complete() || screen.serverState().step() > previous));
    }

    static void use(final TestSingleplayerContext world, final BlockPos position) {
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        });
        world.getConnection().waitForClientboundPackets();
    }

    static void capture(final ClientGameTestContext context, final String name) {
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
        context.takeScreenshot(name);
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
