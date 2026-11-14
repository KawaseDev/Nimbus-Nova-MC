package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.ship.ShipClient;
import cc.nimbusnova.mc.client.ship.ShipScreen;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.ship.ShipMission;
import cc.nimbusnova.mc.ship.ShipPayload;
import cc.nimbusnova.mc.ship.ShipTask;
import cc.nimbusnova.mc.world.ship.ShipMap;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import java.util.EnumSet;

import java.util.Comparator;

final class ShipMissionChecks {
    private ShipMissionChecks() {
        /* w */
    }

    static void run(final ClientGameTestContext context, final TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> server.getPlayerList().op(new NameAndId(world.getConnection().getServerPlayer().getGameProfile())));
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runCommand("ship select");
        context.waitFor(client -> client.level != null && client.level.dimension().equals(ShipMap.DIMENSION) && ShipClient.state() != null);
        context.waitForScreen(ShipScreen.class);
        GuidedMissionChecks.capture(context, "ship-bridge-arrival");
        context.setScreen(() -> null);
        world.getServer().runCommand("gamemode spectator @p");
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p 36 96 57 147 17");
        context.waitTicks(10);
        world.getConnection().waitForChunksRender();
        context.runOnClient(client -> client.gui.hud.toggle());
        GuidedMissionChecks.capture(context, "ship-schematic-exterior");
        context.runOnClient(client -> client.gui.hud.toggle());
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runOnServer(server -> {
            final var level = world.getConnection().getServerLevel();
            for (final ShipMap.Marker marker : ShipMap.MARKERS)
                if (!level.getBlockState(marker.position()).is(ShipMap.block(marker.task())))
                    throw new AssertionError("A schematic task marker was not replaced: " + marker);
        });
        final EnumSet<ShipTask> inspected = EnumSet.noneOf(ShipTask.class);
        for (final ShipMap.Marker marker : ShipMap.MARKERS) {
            if (inspected.add(marker.task()))
                open(context, world, marker.task());
        }
        open(context, world, ShipTask.EXERCISE);
        context.setScreen(() -> null);
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final BlockPos bag = ShipMap.target(ShipTask.EXERCISE, player.blockPosition());
            for (int hit = 0; hit < 5; hit++)
                AttackBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, bag, Direction.UP);
            if (!ShipController.get(server).mission().completed(ShipTask.EXERCISE))
                throw new AssertionError("Five physical bag hits did not complete a finite exercise set");
        });
        open(context, world, ShipTask.ENGINE);
        GuidedMissionChecks.capture(context, "ship-engine-circuit");
        circuit(context);
        open(context, world, ShipTask.STORAGE);
        action(context, 0);
        open(context, world, ShipTask.GALLEY);
        action(context, 0);
        world.getServer().runCommand("ship start");
        resume(context, world);
        align(context, world, ShipTask.PILOT);
        align(context, world, ShipTask.COMMS);
        align(context, world, ShipTask.PILOT);
        resume(context, world);
        open(context, world, ShipTask.ENGINE);
        GuidedMissionChecks.capture(context, "ship-emergency-loads");
        action(context, 1);
        action(context, 2);
        action(context, 3);
        resume(context, world);
        open(context, world, ShipTask.MEDICAL);
        for (int valve = 0; valve < 2; valve++) {
            action(context, valve * 2);
            action(context, valve * 2);
        }
        context.waitFor(client -> client.gui.screen() instanceof ShipScreen screen && screen.state().aux() >>> 16 >= 3);
        GuidedMissionChecks.capture(context, "ship-co2-filter");
        action(context, 6);
        resume(context, world);
        open(context, world, ShipTask.ENGINE);
        circuit(context);
        open(context, world, ShipTask.GALLEY);
        action(context, 0);
        action(context, 1);
        GuidedMissionChecks.capture(context, "ship-heating-budget");
        action(context, 3);
        align(context, world, ShipTask.PILOT);
        align(context, world, ShipTask.COMMS);
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().phase().equals("SUCCESS"));
        final var result = world.getServer().computeOnServer(server -> ShipController.get(server).mission().snapshot());
        if (result.phase() != ShipMission.Phase.SUCCESS || result.battery() < 20 || result.oxygen() < 25)
            throw new AssertionError("The physical ship mission did not recover the crew");
        context.setScreen(() -> null);
        world.getServer().runCommand("execute as @p run ship");
        context.waitForScreen(ShipScreen.class);
        GuidedMissionChecks.capture(context, "ship-success-report");
        world.getServer().runCommand("ship end");
        context.waitFor(client -> client.level != null && !client.level.dimension().equals(ShipMap.DIMENSION));
        System.out.println("Ship runtime checks passed: schematic import, custom stations, circuits, navigation, emergency isolation, CO2 filter, heat budget and full rescue success.");
    }

    private static void resume(final ClientGameTestContext context, final TestSingleplayerContext world) {
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().phase().equals("PAUSED"));
        GuidedMissionChecks.capture(context, "ship-incident-" + ShipClient.state().incident().toLowerCase());
        world.getServer().runCommand("ship acknowledge");
        world.getServer().runCommand("ship resume");
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().phase().equals("ACTIVE"));
    }

    private static void circuit(final ClientGameTestContext context) {
        for (int cell = 0; cell < 9 && !state(context).complete(); cell++) {
            int turns = 0;
            while (!state(context).complete() && (state(context).board() >> cell * 2 & 3) != 0 && turns++ < 4)
                action(context, cell);
        }
        if (!state(context).complete()) throw new AssertionError("The ship circuit did not connect");
    }

    private static void align(final ClientGameTestContext context, final TestSingleplayerContext world, final ShipTask task) {
        open(context, world, task);
        for (int axis = 0; axis < 3; axis++) {
            int turns = 0;
            while (ShipMission.dial(state(context).board(), axis) != ShipMission.dial(state(context).aux(), axis) && turns++ < 5)
                action(context, axis * 2);
        }
        GuidedMissionChecks.capture(context, "ship-" + task.name().toLowerCase() + "-alignment");
        action(context, 6);
    }

    private static ShipPayload state(final ClientGameTestContext context) {
        return context.computeOnClient(client -> ((ShipScreen) client.gui.screen()).state());
    }

    private static void action(final ClientGameTestContext context, final int value) {
        final int revision = state(context).revision();
        context.runOnClient(client -> ((ShipScreen) client.gui.screen()).sendTaskAction(value));
        context.waitFor(client -> client.gui.screen() instanceof ShipScreen screen && screen.state().revision() != revision);
    }

    private static void open(final ClientGameTestContext context, final TestSingleplayerContext world, final ShipTask task) {
        context.setScreen(() -> null);
        final BlockPos station = ShipMap.MARKERS.stream().filter(marker -> marker.task() == task).findFirst().orElseThrow().position();
        final Vec3 feet = world.getServer().computeOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final var level = player.level();
            return BlockPos.betweenClosedStream(station.offset(-3, -2, -3), station.offset(3, 3, 3))
                    .filter(point -> point.getX() != station.getX() || point.getZ() != station.getZ())
                    .filter(point -> level.getBlockState(point.below()).isFaceSturdy(level, point.below(), Direction.UP))
                    .map(Vec3::atBottomCenterOf)
                    .filter(point -> level.noCollision(player, player.getBoundingBox().move(point.subtract(player.position()))))
                    .min(Comparator.comparingDouble(point -> point.distanceToSqr(Vec3.atCenterOf(station)))).orElseThrow();
        });
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p " + feet.x + " " + feet.y + " " + feet.z + " " + Math.toDegrees(Math.atan2(-(station.getX() + 0.5 - feet.x), station.getZ() + 0.5 - feet.z))
                + " " + -Math.toDegrees(Math.atan2(station.getY() + 0.5 - feet.y - 1.62,
                Math.hypot(station.getX() + 0.5 - feet.x, station.getZ() + 0.5 - feet.z))));
        world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().setDeltaMovement(Vec3.ZERO));
        context.waitFor(client -> client.player != null && client.player.position().distanceToSqr(feet) < 4);
        context.waitTicks(5);
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            if (player.distanceToSqr(Vec3.atCenterOf(station)) > 36)
                throw new AssertionError("Station approach is unsafe at " + station + ": player at " + player.position());
        });
        context.runOnClient(client -> client.gui.hud.toggle());
        GuidedMissionChecks.capture(context, "ship-model-" + task.name().toLowerCase());
        context.runOnClient(client -> client.gui.hud.toggle());
        GuidedMissionChecks.use(world, station);
        context.waitForScreen(ShipScreen.class);
        context.waitFor(client -> client.gui.screen() instanceof ShipScreen screen && screen.state().task().equals(task.name()));
    }
}
