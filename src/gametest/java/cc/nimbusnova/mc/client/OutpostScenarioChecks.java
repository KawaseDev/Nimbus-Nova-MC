package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.scenario.ScenarioClient;
import cc.nimbusnova.mc.client.scenario.ScenarioScreen;
import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.scenario.OutpostScenarioController;
import cc.nimbusnova.mc.scenario.ScenarioContent;
import cc.nimbusnova.mc.scenario.ScenarioMission;
import cc.nimbusnova.mc.world.scenario.ScenarioMaps;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class OutpostScenarioChecks {
    private OutpostScenarioChecks() {
        /* w */
    }
    public static void run(final ClientGameTestContext context, final TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> server.getPlayerList().op(new NameAndId(world.getConnection().getServerPlayer().getGameProfile())));
        world.getServer().runCommand("gamemode survival @p");
        select(context, world, "shelter");
        verifyWaypoint(context, "POWER", "Radiation survey station", ScenarioMaps.station(0));
        station(context, world, "shelter", 3);
        station(context, world, "shelter", 0);
        for (int bearing = 0; bearing < 3; bearing++) act(context, 0, bearing);
        station(context, world, "shelter", 1);
        act(context, 1, 1); act(context, 1, 1);
        world.getServer().runCommand("execute in nimbus_nova:scenario_shelter run tp @p 0.5 65 -6.5 180 10");
        world.getServer().runOnServer(server -> {
            final ServerPlayer player = world.getConnection().getServerPlayer();
            equip(player, ScenarioContent.SHIELD.asItem());
            for (final BlockPos target : ScenarioMaps.shieldSlots())
                UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(target.below()).add(0, 0.5, 0), Direction.UP, target.below(), false));
            if (OutpostScenarioController.get(server).mission().shields != 7) throw new AssertionError("Physical shielding was not installed");
            OutpostScenarioController.get(server).action(server, "start");
        });
        station(context, world, "shelter", 2);
        sos(context);
        context.setScreen(() -> null);
        world.getServer().runCommand("execute in nimbus_nova:scenario_shelter run tp @p 0.5 65 -10.5 0 -5");
        context.waitTicks(5);
        GuidedMissionChecks.capture(context, "scenario-shelter-covered-room");
        advance(world, 54);
        station(context, world, "shelter", 0);
        act(context, 0, 3);
        final int liveStart = world.getServer().computeOnServer(server -> OutpostScenarioController.get(server).mission().elapsed);
        advance(world, liveStart + 3);
        context.waitFor(_ -> ScenarioClient.waypoint() != null && ScenarioClient.waypoint().title().equals("Covered shelter"));
        GuidedMissionChecks.capture(context, "scenario-shelter-live-event-scan");
        context.setScreen(() -> null);
        world.getServer().runCommand("execute in nimbus_nova:scenario_shelter run tp @p 0.5 65 -10.5 0 -5");
        advance(world, 150);
        success(context, world, "shelter");
        select(context, world, "long_night");
        verifyWaypoint(context, "MAINTENANCE", "Air loop and lab recorder", ScenarioMaps.station(1));
        station(context, world, "long_night", 3);
        station(context, world, "long_night", 1);
        act(context, 1, 0); act(context, 1, 2); act(context, 1, 2);
        world.getServer().runOnServer(server -> OutpostScenarioController.get(server).action(server, "start"));
        advance(world, 30);
        station(context, world, "long_night", 0);
        act(context, 0, 2);
        advance(world, 45);
        context.waitFor(_ -> ScenarioClient.waypoint() != null && ScenarioClient.waypoint().title().equals("Failed power junction"));
        context.setScreen(() -> null);
        moveToStation(world, "long_night", 0);
        world.getServer().runOnServer(server -> {
            final ServerPlayer player = world.getConnection().getServerPlayer();
            equip(player, RepairContent.WIRE_KIT);
            final var controller = OutpostScenarioController.get(server);
            controller.action(server, "pause");
            final int before = player.getMainHandItem().getCount();
            final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ScenarioMaps.station(0)), Direction.NORTH, ScenarioMaps.station(0), false);
            final InteractionResult denied = UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit);
            if (denied != InteractionResult.FAIL || player.getMainHandItem().getCount() != before || controller.mission().repaired)
                throw new AssertionError("Paused wire repair consumed a kit or cleared the fault");
            controller.action(server, "resume");
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit);
            if (!OutpostScenarioController.get(server).mission().repaired) throw new AssertionError("Night cable repair failed");
        });
        station(context, world, "long_night", 2);
        sos(context);
        station(context, world, "long_night", 0);
        act(context, 0, 3);
        advance(world, 150);
        success(context, world, "long_night");
        select(context, world, "greenhouse");
        station(context, world, "greenhouse", 3);
        station(context, world, "greenhouse", 0);
        act(context, 0, 3);
        for (int bed = 0; bed < 3; bed++) act(context, 0, bed);
        station(context, world, "greenhouse", 2);
        act(context, 2, 3);
        world.getServer().runOnServer(server -> OutpostScenarioController.get(server).action(server, "start"));
        context.runOnClient(_ -> ScenarioClient.open());
        context.waitFor(_ -> ScenarioClient.state() != null && ScenarioClient.state().get("phase").getAsString().equals("ACTIVE")
                && ScenarioClient.state().get("station").getAsInt() < 0);
        context.waitForScreen(ScenarioScreen.class);
        verifyWaypoint(context, "MAINTENANCE", "Pump and grow lights", ScenarioMaps.station(0));
        station(context, world, "greenhouse", 1);
        for (int second = 0; second < 150; second++) {
            final int current = world.getServer().computeOnServer(server -> OutpostScenarioController.get(server).mission().elapsed);
            if (current >= 150) break;
            final var state = ScenarioClient.state();
            for (int bed = 0; bed < 3; bed++) {
                final double moisture = state.getAsJsonArray("moisture").get(bed).getAsDouble();
                final boolean open = (state.get("valves").getAsInt() & 1 << bed) != 0;
                if (moisture < 48 && !open || moisture > 62 && open) act(context, 1, bed);
            }
            final double humidity = ScenarioClient.state().get("cabinHumidity").getAsDouble();
            final boolean vent = ScenarioClient.state().get("extractionVent").getAsBoolean();
            if (humidity < 55 && vent || humidity > 65 && !vent) {
                station(context, world, "greenhouse", 2);
                act(context, 2, 3);
                station(context, world, "greenhouse", 1);
            }
            advance(world, current + 1);
            context.waitFor(client -> ScenarioClient.state() != null && ScenarioClient.state().get("elapsed").getAsInt() >= current + 1);
            if (ScenarioClient.state().get("pipeBroken").getAsBoolean()) {
                context.waitFor(_ -> ScenarioClient.waypoint() != null && ScenarioClient.waypoint().title().equals("Feed pipe gap"));
                context.setScreen(() -> null);
                world.getServer().runCommand("execute in nimbus_nova:scenario_greenhouse run tp @p -4.5 65 1.5 0 15");
                world.getServer().runOnServer(server -> {
                    final ServerPlayer player = world.getConnection().getServerPlayer();
                    equip(player, RepairContent.PATCH_PLATE.asItem());
                    final var controller = OutpostScenarioController.get(server);
                    final int before = player.getMainHandItem().getCount();
                    final BlockPos wrong = OutpostScenarioController.PIPE.east(), wrongFloor = wrong.below();
                    final var original = player.level().getBlockState(wrong);
                    final InteractionResult rejected = UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(wrongFloor).add(0, 0.5, 0), Direction.UP, wrongFloor, false));
                    if (rejected != InteractionResult.FAIL || player.getMainHandItem().getCount() != before || player.level().getBlockState(wrong) != original)
                        throw new AssertionError("Wrong-target placement did not preserve the finite patch plate");
                    final BlockPos floor = OutpostScenarioController.PIPE.below();
                    final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
                    controller.action(server, "pause");
                    final InteractionResult denied = UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit);
                    if (denied != InteractionResult.FAIL || player.getMainHandItem().getCount() != before || !player.level().getBlockState(OutpostScenarioController.PIPE).isAir())
                        throw new AssertionError("Paused patching consumed a plate or changed the world");
                    controller.action(server, "resume");
                    UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit);
                    if (!OutpostScenarioController.get(server).mission().repaired) throw new AssertionError("Greenhouse physical pipe patch failed");
                });
                station(context, world, "greenhouse", 1);
            }
            if (current >= 80 && current < 140 && ScenarioClient.state().get("harvest").getAsInt() != 7) {
                station(context, world, "greenhouse", 2);
                for (int bed = 0; bed < 3; bed++) {
                    final var fresh = ScenarioClient.state();
                    final double moisture = fresh.getAsJsonArray("moisture").get(bed).getAsDouble();
                    if ((fresh.get("harvest").getAsInt() & 1 << bed) == 0 && moisture >= 40 && moisture <= 70 && fresh.getAsJsonArray("rootAir").get(bed).getAsDouble() >= 60)
                        act(context, 2, bed);
                }
                station(context, world, "greenhouse", 1);
            }
        }
        success(context, world, "greenhouse");
        System.out.println("Outpost scenario runtime checks passed: shelter construction/event, polar power repair/beacon and greenhouse irrigation/physical pipe repair.");
    }

    private static void verifyWaypoint(final ClientGameTestContext context, final String role, final String title, final BlockPos target) {
        context.clickScreenButton(role);
        context.waitFor(_ -> {
            final var waypoint = ScenarioClient.waypoint();
            return waypoint != null && waypoint.title().equals(title) && waypoint.targetX() == target.getX()
                    && waypoint.targetY() == target.getY() && waypoint.targetZ() == target.getZ();
        });
        context.setScreen(() -> null);
        GuidedMissionChecks.capture(context, "waypoint-" + ScenarioClient.state().get("id").getAsString());
    }

    private static void select(final ClientGameTestContext context, final TestSingleplayerContext world, final String id) {
        context.setScreen(() -> null);
        world.getServer().runOnServer(server -> OutpostScenarioController.get(server).select(server, id));
        context.waitFor(client -> client.level != null && client.level.dimension().equals(ScenarioMaps.dimension(id)) && ScenarioClient.state() != null);
        context.waitForScreen(ScenarioScreen.class);
        FullscreenChecks.capture(context, "scenario-" + id + "-arrival");
    }
    private static void success(final ClientGameTestContext context, final TestSingleplayerContext world, final String id) {
        context.waitFor(client -> ScenarioClient.state() != null && ScenarioClient.state().get("phase").getAsString().equals("SUCCESS"));
        world.getServer().runOnServer(server -> OutpostScenarioController.get(server).open(world.getConnection().getServerPlayer()));
        context.waitForScreen(ScenarioScreen.class);
        GuidedMissionChecks.capture(context, "scenario-" + id + "-success");
        world.getServer().runOnServer(server -> OutpostScenarioController.get(server).end(server));
        context.waitFor(client -> ScenarioClient.state() == null);
    }
    private static void station(final ClientGameTestContext context, final TestSingleplayerContext world, final String id, final int index) {
        context.setScreen(() -> null);
        moveToStation(world, id, index);
        context.waitTicks(3);
        GuidedMissionChecks.use(world, ScenarioMaps.station(index));
        context.waitForScreen(ScenarioScreen.class);
        context.waitFor(client -> ((ScenarioScreen) client.gui.screen()).state().get("station").getAsInt() == index);
    }

    private static void moveToStation(final TestSingleplayerContext world, final String id, final int index) {
        final BlockPos target = ScenarioMaps.station(index);
        world.getServer().runCommand("execute in nimbus_nova:scenario_" + id + " run tp @p " + (target.getX() + 0.5)
                + " 65 " + (target.getZ() - 1.5) + " 0 15");
    }
    private static void act(final ClientGameTestContext context, final int station, final int value) {
        final int revision = ScenarioClient.state().get("revision").getAsInt();
        context.runOnClient(client -> ((ScenarioScreen) client.gui.screen()).sendTaskAction(station, value));
        context.waitFor(client -> ScenarioClient.state() != null && ScenarioClient.state().get("revision").getAsInt() != revision);
    }
    private static void sos(final ClientGameTestContext context) {
        for (final int pulse : new int[] { 0, 0, 0, 1, 1, 1, 0, 0, 0 }) act(context, 2, pulse);
    }
    private static void advance(final TestSingleplayerContext world, final int second) {
        world.getServer().runOnServer(server -> {
            final ScenarioMission mission = OutpostScenarioController.get(server).mission();
            for (int tick = 0; tick < 3200 && mission.elapsed < second && mission.phase == ScenarioMission.Phase.ACTIVE; tick++)
                ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
        });
    }
    private static void equip(final ServerPlayer player, final Item item) {
        if (player.getMainHandItem().is(item)) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (!player.getInventory().getItem(slot).is(item)) continue;
            final ItemStack old = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().removeItem(slot, 64));
            if (!old.isEmpty()) player.getInventory().add(old);
            return;
        }
        throw new AssertionError("Missing scenario supply " + item);
    }
}
