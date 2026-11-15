package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.ship.ShipClient;
import cc.nimbusnova.mc.client.hub.HubScreen;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.ship.rooms.ShipRooms;
import cc.nimbusnova.mc.client.ship.ShipScreen;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.ship.ShipMission;
import cc.nimbusnova.mc.ship.ShipPayload;
import cc.nimbusnova.mc.ship.ShipTask;
import cc.nimbusnova.mc.ship.ShipHoldActions;
import cc.nimbusnova.mc.ship.ShipActionPayload;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.client.ship.controls.HoldTaskButton;
import cc.nimbusnova.mc.client.suit.SuitLifeSupportClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import cc.nimbusnova.mc.world.ship.ShipMap;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
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
        world.getServer().runCommand("execute as @p run nimbus");
        context.waitForScreen(HubScreen.class);
        FullscreenChecks.capture(context, "mission-hub");
        context.setScreen(() -> null);
        world.getServer().runCommand("gamemode spectator @p");
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p 36 96 57 147 17");
        context.waitTicks(10);
        world.getConnection().waitForChunksRender();
        context.runOnClient(client -> client.gui.hud.toggle());
        GuidedMissionChecks.capture(context, "ship-schematic-exterior");
        FullscreenChecks.capture(context, "ship-orbit-and-stars");
        context.runOnClient(client -> client.gui.hud.toggle());
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runOnServer(server -> {
            final var level = world.getConnection().getServerLevel();
            for (final ShipMap.Marker marker : ShipMap.MARKERS)
                if (!level.getBlockState(marker.position()).is(ShipMap.block(marker.task())))
                    throw new AssertionError("A schematic task marker was not replaced: " + marker);
        });
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p 0.5 76 -17.5 180 -8");
        context.waitTicks(10);
        world.getConnection().waitForChunksRender();
        context.runOnClient(client -> client.gui.hud.toggle());
        GuidedMissionChecks.capture(context, "ship-sealed-panel-seams");
        context.runOnClient(client -> client.gui.hud.toggle());
        final EnumSet<ShipTask> inspected = EnumSet.noneOf(ShipTask.class);
        for (final ShipMap.Marker marker : ShipMap.MARKERS) {
            if (inspected.add(marker.task()))
                open(context, world, marker.task());
        }
        open(context, world, ShipTask.EXERCISE);
        context.setScreen(() -> null);
        for (int hit = 0; hit < 5; hit++) {
            world.getServer().runOnServer(server -> {
                final var player = world.getConnection().getServerPlayer();
                final BlockPos bag = ShipMap.target(ShipTask.EXERCISE, player.blockPosition());
                AttackBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, bag, Direction.UP);
            });
            if (hit == 0) {
                context.waitTicks(1);
                GuidedMissionChecks.capture(context, "ship-exercise-swing");
            }
            context.waitTicks(7);
        }
        world.getServer().runOnServer(server -> {
            if (!ShipController.get(server).mission().completed(ShipTask.EXERCISE))
                throw new AssertionError("Five paced physical hits did not finish the exercise set");
        });
        open(context, world, ShipTask.ENGINE);
        circuit(context);
        open(context, world, ShipTask.STORAGE);
        action(context, 0);
        action(context, 2);
        verifyHeldCargo(context, world);
        open(context, world, ShipTask.GALLEY);
        for (final int operation : new int[] { 0, 0, 1, 2, 2 })
            action(context, operation);
        GuidedMissionChecks.capture(context, "ship-ration-metering");
        action(context, 6);
        open(context, world, ShipTask.PLANTS);
        for (final int operation : new int[] { 0, 3, 3, 2, 5 })
            action(context, operation);
        GuidedMissionChecks.capture(context, "ship-coupled-irrigation");
        action(context, 8);
        open(context, world, ShipTask.MEDICAL);
        for (final int operation : new int[] { 0, 1, 2, 4 })
            action(context, operation);
        GuidedMissionChecks.capture(context, "ship-sensor-diagnostics");
        action(context, 6);
        open(context, world, ShipTask.SCIENCE);
        for (final int operation : new int[] { 2, 3, 7 })
            action(context, operation);
        GuidedMissionChecks.capture(context, "ship-specimen-classification");
        action(context, 9);
        RoomLightingChecks.run(context, world);
        RoomEmergencyChecks.collect(context, world);
        RoomEmergencyChecks.doors(context, world);
        world.getServer().runCommand("ship start");
        resume(context, world);
        align(context, world, ShipTask.PILOT);
        align(context, world, ShipTask.COMMS);
        align(context, world, ShipTask.PILOT);
        align(context, world, ShipTask.COMMS);
        resume(context, world);
        RoomEmergencyChecks.repairPower(context, world);
        open(context, world, ShipTask.ENGINE);
        GuidedMissionChecks.capture(context, "ship-emergency-loads");
        action(context, 1);
        action(context, 2);
        action(context, 3);
        resume(context, world);
        RoomEmergencyChecks.patchHull(context, world);
        open(context, world, ShipTask.MEDICAL);
        for (final int component : new int[] { 2, 0, 3, 1 })
            action(context, component);
        action(context, 4);
        context.waitFor(client -> client.gui.screen() instanceof ShipScreen screen && screen.state().aux() >= 3);
        GuidedMissionChecks.capture(context, "ship-co2-adapter-test");
        action(context, 5);
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
        world.getServer().runOnServer(server -> ModContent.MISSION_TABLET.use(world.getConnection().getServerLevel(),
                world.getConnection().getServerPlayer(), InteractionHand.MAIN_HAND));
        context.waitForScreen(ShipScreen.class);
        GuidedMissionChecks.capture(context, "ship-success-report");
        world.getServer().runOnServer(server -> {
            ShipRooms.recoverOwnedBreach(server);
            final var level = world.getConnection().getServerLevel();
            if (level.getBlockState(ShipRooms.breachPosition()).isAir()
                    || level.getBlockState(ShipRooms.breachPosition()).is(RepairContent.PATCH_PLATE))
                throw new AssertionError("Owned breach recovery did not restore the original hull");
        });
        world.getServer().runCommand("ship end");
        context.waitFor(client -> client.level != null && !client.level.dimension().equals(ShipMap.DIMENSION));
        exploration(context, world);
        canceledDescent(context, world);
        System.out.println("Ship runtime checks passed: schematic import, custom stations, cartridge assembly, momentum docking, star navigation, signal tuning, CO2 adapter, timed burn, heat budget and full rescue success.");
    }

    static void exploration(final ClientGameTestContext context, final TestSingleplayerContext world) {
        exploration(context, world, false);
    }

    static void canceledDescent(final ClientGameTestContext context, final TestSingleplayerContext world) {
        exploration(context, world, true);
    }

    private static void exploration(final ClientGameTestContext context, final TestSingleplayerContext world, final boolean cancelLanding) {
        world.getServer().runOnServer(server -> server.getPlayerList().op(new NameAndId(world.getConnection().getServerPlayer().getGameProfile())));
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runCommand("ship explore");
        context.waitFor(client -> client.level != null && client.level.dimension().equals(ShipMap.DIMENSION)
                && ShipClient.state() != null && ShipClient.state().variant().equals("EXPLORATION"));
        context.waitForScreen(ShipScreen.class);
        context.clickScreenButton("pilot");
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().role().equals("PILOT"));
        context.clickScreenButton("Report preflight check");
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().feedback().contains("PILOT"));
        if (world.getServer().computeOnServer(server -> ShipController.get(server).mission().preflightReports()) != 1 << MoonMission.Role.PILOT.ordinal())
            throw new AssertionError("The selected role's preflight report must reach the server");
        world.getServer().runOnServer(server -> {
            final var moon = server.getLevel(LunarSurface.DIMENSION);
            final BlockPos playerBuild = new BlockPos(20, 63, 20);
            moon.setBlock(playerBuild, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            try { LunarSurface.create(server); } catch (java.io.IOException error) { throw new RuntimeException(error); }
            if (!moon.getBlockState(playerBuild).is(Blocks.GOLD_BLOCK))
                throw new AssertionError("Selecting the Moon again must preserve existing builds");
        });
        open(context, world, ShipTask.ENGINE); circuit(context);
        open(context, world, ShipTask.STORAGE); action(context, 0); action(context, 2); action(context, 3);
        world.getServer().runCommand("ship start");
        resume(context, world);
        align(context, world, ShipTask.PILOT);
        align(context, world, ShipTask.COMMS);
        align(context, world, ShipTask.PILOT);
        align(context, world, ShipTask.COMMS);
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().stage().equals("ORBIT"));
        open(context, world, ShipTask.PILOT);
        GuidedMissionChecks.capture(context, "moon-orbit-capture");
        action(context, 0); action(context, 3);
        if (!state(context).stage().equals("ORBIT")) throw new AssertionError("An insufficient burn was accepted");
        action(context, 1); action(context, 3);
        if (!state(context).stage().equals("ORBIT")) throw new AssertionError("Capture must wait for a power check");
        open(context, world, ShipTask.ENGINE); action(context, 0);
        open(context, world, ShipTask.PILOT); action(context, 3);
        resume(context, world);
        open(context, world, ShipTask.PILOT);
        if (cancelLanding) {
            if (!context.computeOnClient(client -> client.gui.screen().children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("Hold to cancel landing"))))
                throw new AssertionError("The pilot needs a visible landing cancellation control");
            action(context, 6);
            if (!state(context).stage().equals("RETURN")) throw new AssertionError("Canceling descent must start the return");
            align(context, world, ShipTask.PILOT);
            align(context, world, ShipTask.COMMS);
            context.waitFor(client -> ShipClient.state() != null && ShipClient.state().phase().equals("SUCCESS"));
            if (!world.getServer().computeOnServer(server -> ShipController.get(server).mission().snapshot().outcome().contains("Landing canceled")))
                throw new AssertionError("The successful return must report the canceled landing");
            world.getServer().runCommand("ship end");
            context.waitFor(client -> client.level != null && !client.level.dimension().equals(ShipMap.DIMENSION));
            return;
        }
        action(context, 0);
        if ((state(context).board() & 256) != 0) throw new AssertionError("Guidance must wait for maintenance");
        align(context, world, ShipTask.COMMS);
        open(context, world, ShipTask.PILOT);
        GuidedMissionChecks.capture(context, "moon-descent-controls");
        for (final int operation : new int[] { 0, 5, 1, 2, 3, 3, 3, 4 }) action(context, operation);
        resume(context, world);
        world.getServer().runCommand("execute as @p run ship surface");
        if (world.getServer().computeOnServer(server -> world.getConnection().getServerPlayer().level().dimension().equals(LunarSurface.DIMENSION)))
            throw new AssertionError("The surface must require a suit check and equipment");
        open(context, world, ShipTask.MEDICAL); action(context, 0);
        if (world.getServer().computeOnServer(server -> ShipController.get(server).mission().canVisitSurface()))
            throw new AssertionError("Surface access must wait for a verified communication link");
        align(context, world, ShipTask.COMMS);
        open(context, world, ShipTask.MEDICAL); action(context, 0);
        world.getServer().runCommand("execute as @p run ship suit");
        world.getServer().runCommand("execute as @p run ship surface");
        context.waitFor(client -> client.level != null && client.level.dimension().equals(LunarSurface.DIMENSION));
        final double gravity = world.getServer().computeOnServer(server -> world.getConnection().getServerPlayer().getGravity());
        if (Math.abs(gravity - 0.08 * LunarSurface.GRAVITY_RATIO) > 0.000001)
            throw new AssertionError("The Moon must use the configured one-sixth gravity");
        context.waitFor(client -> SuitLifeSupportClient.state() != null && !SuitLifeSupportClient.state().frozen());
        world.getServer().runCommand("ship pause");
        context.waitFor(client -> SuitLifeSupportClient.state() != null && SuitLifeSupportClient.state().frozen());
        final double pausedAir = context.computeOnClient(client -> SuitLifeSupportClient.state().air());
        context.waitTicks(30);
        if (context.computeOnClient(client -> SuitLifeSupportClient.state().air()) != pausedAir)
            throw new AssertionError("A teacher pause must freeze lunar suit air");
        world.getServer().runCommand("ship resume");
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().phase().equals("ACTIVE"));
        context.setScreen(() -> null);
        world.getConnection().waitForChunksRender();
        GuidedMissionChecks.capture(context, "moon-surface");
        world.getServer().runCommand("execute in nimbus_nova:lunar_surface run tp @p 9.5 63 -8.5");
        GuidedMissionChecks.use(world, LunarSurface.SAMPLE);
        if (!world.getServer().computeOnServer(server -> ShipController.get(server).mission().completed(ShipTask.SCIENCE)))
            throw new AssertionError("Right-clicking the lunar sample must complete the sample task");
        world.getServer().runCommand("execute as @p run ship join");
        context.waitFor(client -> client.level != null && client.level.dimension().equals(ShipMap.DIMENSION));
        open(context, world, ShipTask.SCIENCE);
        if (!context.computeOnClient(client -> client.gui.screen().children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("NASA sample image"))))
            throw new AssertionError("The collected sample must offer its NASA comparison");
        open(context, world, ShipTask.PILOT);
        GuidedMissionChecks.capture(context, "moon-ascent-controls");
        action(context, 5); action(context, 6);
        align(context, world, ShipTask.PILOT);
        align(context, world, ShipTask.COMMS);
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().phase().equals("SUCCESS"));
        world.getServer().runCommand("ship end");
        context.waitFor(client -> client.level != null && !client.level.dimension().equals(ShipMap.DIMENSION));
        System.out.println("Moon exploration prototype checks passed: capture, guidance, safe descent, suit, sample, ascent and return.");
    }

    private static void resume(final ClientGameTestContext context, final TestSingleplayerContext world) {
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().phase().equals("PAUSED"));
        GuidedMissionChecks.capture(context, "ship-incident-" + ShipClient.state().incident().toLowerCase());
        LearningGameChecks.solve(context, world, "ship");
        world.getServer().runCommand("ship acknowledge");
        world.getServer().runCommand("ship resume");
        context.waitFor(client -> ShipClient.state() != null && ShipClient.state().phase().equals("ACTIVE"));
    }

    private static void circuit(final ClientGameTestContext context) {
        for (int socket = 0; socket < 4 && !state(context).complete(); socket++) {
            final int cartridge = state(context).aux() >> socket * 4 & 15;
            action(context, socket * 4 + cartridge - 1);
        }
        if (!state(context).complete()) throw new AssertionError("The power cartridges did not energize the bus");
    }

    private static void align(final ClientGameTestContext context, final TestSingleplayerContext world, final ShipTask task) {
        open(context, world, task);
        if (task == ShipTask.COMMS) {
            final int carrier = state(context).stage().equals("RETURN") ? 5 : state(context).stage().equals("DESCENT") ? 4 : 3;
            for (int step = 0; step < 8 && (state(context).board() & 15) != carrier; step++)
                action(context, (state(context).board() & 15) < carrier ? 0 : 1);
            for (int step = 0; step < 4 && (state(context).board() >> 4 & 7) != 2; step++)
                action(context, (state(context).board() >> 4 & 7) < 2 ? 2 : 3);
            GuidedMissionChecks.capture(context, "ship-signal-reconstruction");
            action(context, 4);
        } else if (state(context).stage().equals("DOCKING")) {
            for (final int impulse : new int[] { 1, 1, 0, 0, 3, 3, 2, 2 })
                action(context, impulse);
            GuidedMissionChecks.capture(context, "ship-momentum-docking");
        FullscreenChecks.capture(context, "momentum-docking");
            action(context, 5);
        } else if (state(context).stage().equals("NAVIGATION")) {
            for (final int star : new int[] { 1, 3, 5 })
                action(context, star);
            GuidedMissionChecks.capture(context, "ship-constellation-fix");
            action(context, 6);
        } else {
            action(context, 3);
            action(context, 0);
            context.waitFor(client -> client.gui.screen() instanceof ShipScreen screen && (screen.state().board() & 255) >= 6);
            GuidedMissionChecks.capture(context, "ship-return-burn-window");
            action(context, 1);
        }
    }

    private static ShipPayload state(final ClientGameTestContext context) {
        return context.computeOnClient(client -> ((ShipScreen) client.gui.screen()).state());
    }

    private static void action(final ClientGameTestContext context, final int value) {
        final int revision = state(context).revision();
        final ShipTask task = ShipTask.valueOf(state(context).task());
        final MoonMission.Stage stage = MoonMission.Stage.valueOf(state(context).stage());
        if (ShipHoldActions.requires(task, stage, value)) {
            context.runOnClient(client -> ((ShipScreen) client.gui.screen()).beginHeldTaskAction(value));
            context.waitTicks(ShipHoldActions.durationTicks(task, stage, value) + 5);
            context.runOnClient(client -> ((ShipScreen) client.gui.screen()).finishHeldTaskAction(value));
        } else
            context.runOnClient(client -> ((ShipScreen) client.gui.screen()).sendTaskAction(value));
        context.waitFor(client -> client.gui.screen() instanceof ShipScreen screen && screen.state().revision() != revision);
    }

    private static void verifyHeldCargo(final ClientGameTestContext context, final TestSingleplayerContext world) {
        final ShipPayload before = state(context);
        context.runOnClient(client -> ClientPlayNetworking.send(new ShipActionPayload("task", before.station(), before.revision(), 3)));
        context.waitTicks(4);
        context.runOnClient(client -> {
            final var screen = (ShipScreen) client.gui.screen();
            screen.beginHeldTaskAction(3);
            screen.finishHeldTaskAction(3);
        });
        context.waitTicks(4);
        if (state(context).revision() != before.revision() || state(context).complete())
            throw new AssertionError("Instant or early-finished holds committed cargo");
        focusHold(context);
        context.getInput().holdKey(InputConstants.KEY_SPACE);
        context.waitTicks(5);
        context.getInput().releaseKey(InputConstants.KEY_SPACE);
        context.waitTicks(3);
        if (state(context).revision() != before.revision())
            throw new AssertionError("Releasing the hold early changed shared resources");
        focusHold(context);
        context.getInput().holdKey(InputConstants.KEY_SPACE);
        context.waitTicks(12);
        GuidedMissionChecks.capture(context, "ship-cargo-hold-charging");
        try {
            context.waitFor(client -> client.gui.screen() instanceof ShipScreen screen && screen.state().revision() != before.revision());
        } finally {
            context.getInput().releaseKey(InputConstants.KEY_SPACE);
        }
        if (!state(context).complete())
            throw new AssertionError("A continuous physical Space hold did not commit the balanced cargo");
        world.getConnection().waitForClientboundPackets();
        System.out.println("Hold runtime checks passed: direct-click rejection, early-finish rejection, physical release cancellation and continuous keyboard hold.");
    }

    private static void focusHold(final ClientGameTestContext context) {
        context.runOnClient(client -> {
            final var screen = (ShipScreen) client.gui.screen();
            final HoldTaskButton button = screen.children().stream().filter(HoldTaskButton.class::isInstance)
                    .map(HoldTaskButton.class::cast).findFirst().orElseThrow();
            screen.setFocused(button);
            button.setFocused(true);
        });
    }

    private static void open(final ClientGameTestContext context, final TestSingleplayerContext world, final ShipTask task) {
        context.setScreen(() -> null);
        final BlockPos station = ShipMap.MARKERS.stream().filter(marker -> marker.task() == task).findFirst().orElseThrow().position();
        final Approach approach = world.getServer().computeOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final var level = player.level();
            final var targets = level.getBlockState(station).getShape(level, station).toAabbs().stream()
                    .map(box -> box.getCenter().add(Vec3.atLowerCornerOf(station))).toList();
            return BlockPos.betweenClosedStream(station.offset(-3, -2, -3), station.offset(3, 3, 3))
                    .filter(point -> point.getX() != station.getX() || point.getZ() != station.getZ())
                    .filter(point -> level.getBlockState(point.below()).isFaceSturdy(level, point.below(), Direction.UP))
                    .map(Vec3::atBottomCenterOf)
                    .filter(point -> level.noCollision(player, player.getBoundingBox().move(point.subtract(player.position()))))
                    .flatMap(feet -> targets.stream().filter(target -> level.clip(new ClipContext(feet.add(0, 1.62, 0), target,
                            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).getBlockPos().equals(station))
                            .map(target -> new Approach(feet, target)))
                    .min(Comparator.comparingDouble(candidate -> Math.abs(candidate.feet.distanceToSqr(Vec3.atCenterOf(station)) - 4)))
                    .orElseThrow(() -> new AssertionError("No visible, standable approach to " + task + " at " + station));
        });
        final Vec3 feet = approach.feet;
        final Vec3 direction = approach.focus.subtract(feet.add(0, 1.62, 0));
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p " + feet.x + " " + feet.y + " " + feet.z
                + " " + Math.toDegrees(Math.atan2(-direction.x, direction.z))
                + " " + -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
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
    private record Approach(Vec3 feet, Vec3 focus) {
        /* w */
    }

}
