package cc.nimbusnova.mc.server;

import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.moon.MoonMission.Role;
import cc.nimbusnova.mc.scenario.OutpostScenarioController;
import cc.nimbusnova.mc.scenario.ScenarioActionPayload;
import cc.nimbusnova.mc.scenario.ScenarioContent;
import cc.nimbusnova.mc.scenario.ScenarioMission;
import cc.nimbusnova.mc.world.scenario.ScenarioMaps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;

final class ScenarioRuntimeChecks {
    private final GameTestHelper context;
    private final MinecraftServer server;
    private final OutpostScenarioController controller;
    private final Map<Role, ServerPlayer> crew;
    private ServerLevel level;

    private ScenarioRuntimeChecks(final GameTestHelper context, final Map<Role, ServerPlayer> crew) {
        this.context = context;
        this.crew = crew;
        server = context.getLevel().getServer();
        controller = OutpostScenarioController.get(server);
    }

    static void run(final GameTestHelper context, final Map<Role, ServerPlayer> crew) {
        final ScenarioRuntimeChecks test = new ScenarioRuntimeChecks(context, crew);
        test.shelter();
        test.longNight();
        test.greenhouse();
    }

    private void shelter() {
        select("shelter");
        for (int bearing = 0; bearing < 3; bearing++) task(Role.POWER, 0, bearing);
        task(Role.LIFE_SUPPORT, 1, 1);
        task(Role.LIFE_SUPPORT, 1, 1);
        issueAndShare(ScenarioContent.SHIELD.asItem(), 3);
        for (final BlockPos slot : ScenarioMaps.shieldSlots()) repair(slot.below());
        context.assertTrue(controller.mission().shields == 7, "All three shelter slots need real shielding blocks");
        controller.action(server, "start");
        sos();
        for (final ServerPlayer actor : crew.values()) move(actor, new BlockPos(0, 65, -10));
        advance(54);
        task(Role.POWER, 0, 3);
        advance(57);
        context.assertTrue(controller.mission().observation, "The live outside scan did not complete");
        move(crew.get(Role.POWER), new BlockPos(0, 65, -10));
        advance(150);
        context.assertTrue(controller.mission().dose == 9, "Three exposed scan seconds should cost nine dose units");
        finish();
    }

    private void longNight() {
        select("long_night");
        issueAndShare(RepairContent.WIRE_KIT, 2);
        task(Role.MAINTENANCE, 1, 0);
        task(Role.LIFE_SUPPORT, 1, 2);
        task(Role.LIFE_SUPPORT, 1, 2);
        controller.action(server, "start");
        advance(30);
        task(Role.POWER, 0, 2);
        advance(45);
        context.assertTrue(controller.mission().cableBroken, "The scripted night cable fault did not occur");
        repair(ScenarioMaps.station(0));
        sos();
        task(Role.POWER, 0, 3);
        advance(150);
        finish();
    }

    private void greenhouse() {
        select("greenhouse");
        issueAndShare(RepairContent.PATCH_PLATE.asItem(), 2);
        task(Role.MAINTENANCE, 0, 3);
        for (int bed = 0; bed < 3; bed++) task(Role.POWER, 0, bed);
        task(Role.PILOT, 2, 3);
        controller.action(server, "start");
        final ScenarioMission mission = controller.mission();
        for (int second = 0; second < 150; second++) {
            for (int bed = 0; bed < 3; bed++) {
                final boolean open = (mission.valves & 1 << bed) != 0;
                if (mission.moisture[bed] < 48 && !open || mission.moisture[bed] > 62 && open)
                    task(Role.LIFE_SUPPORT, 1, bed);
            }
            if (mission.cabinHumidity < 55 && mission.extractionVent || mission.cabinHumidity > 65 && !mission.extractionVent)
                task(Role.PILOT, 2, 3);
            advance(second + 1);
            if (mission.pipeBroken) {
                context.assertTrue(level.getBlockState(OutpostScenarioController.PIPE).isAir(), "The greenhouse event did not open a physical pipe gap");
                repair(OutpostScenarioController.PIPE.below());
            }
            if (second >= 80 && second < 140 && mission.harvest != 7 && mission.cabinHumidity >= 40 && mission.cabinHumidity <= 70) {
                for (int bed = 0; bed < 3; bed++)
                    if ((mission.harvest & 1 << bed) == 0 && mission.moisture[bed] >= 40 && mission.moisture[bed] <= 70
                            && mission.rootAir[bed] >= 60 && mission.lightSeconds[bed] >= 10)
                        task(Role.SUPPLIES, 2, bed);
            }
        }
        finish();
    }

    private void select(final String id) {
        controller.select(server, id);
        level = server.getLevel(ScenarioMaps.dimension(id));
        if (level == null) throw new AssertionError("Scenario world is unavailable: " + id);
        for (final var entry : crew.entrySet())
            controller.playerAction(entry.getValue(), new ScenarioActionPayload("role", 0, entry.getKey().ordinal(), controller.mission().revision));
    }

    private void task(final Role role, final int station, final int value) {
        final ServerPlayer actor = crew.get(role);
        final BlockPos target = ScenarioMaps.station(station).south();
        if (actor.level() != level || !actor.blockPosition().equals(target)) move(actor, target);
        final ScenarioMission mission = controller.mission();
        final int revision = mission.revision;
        controller.playerAction(actor, new ScenarioActionPayload("task", station, value, revision));
        context.assertTrue(mission.revision > revision, "Scenario task rejected: " + mission.id + "/" + role + "/" + station + "/" + value + ": " + mission.feedback);
    }

    private void issueAndShare(final Item item, final int amount) {
        final ServerPlayer supplies = crew.get(Role.SUPPLIES);
        move(supplies, ScenarioMaps.station(3).south());
        controller.collectSupplies(supplies);
        final ServerPlayer maintenance = crew.get(Role.MAINTENANCE);
        for (int slot = 0; slot < supplies.getInventory().getContainerSize(); slot++) {
            if (!supplies.getInventory().getItem(slot).is(item)) continue;
            final ItemStack stack = supplies.getInventory().removeItem(slot, amount);
            if (stack.getCount() != amount) break;
            final ItemStack previous = maintenance.getMainHandItem().copy();
            maintenance.setItemInHand(InteractionHand.MAIN_HAND, stack);
            if (!previous.isEmpty() && !maintenance.addItem(previous)) throw new AssertionError("Fixture inventory was full");
            return;
        }
        throw new AssertionError("The supplies actor did not collect " + amount + " × " + item);
    }

    private void repair(final BlockPos clicked) {
        final ServerPlayer maintenance = crew.get(Role.MAINTENANCE);
        move(maintenance, clicked.south().atY(65));
        final Direction face = controller.mission().id.equals("long_night") ? Direction.SOUTH : Direction.UP;
        final int before = maintenance.getMainHandItem().getCount();
        final InteractionResult result = UseBlockCallback.EVENT.invoker().interact(maintenance, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked).add(face.getUnitVec3().scale(0.5)), face, clicked, false));
        context.assertTrue(result == InteractionResult.SUCCESS && maintenance.getMainHandItem().getCount() == before - 1,
                "The physical repair callback did not use exactly one item: " + controller.mission().id + "/" + clicked);
    }

    private void sos() {
        for (final int pulse : new int[] { 0, 0, 0, 1, 1, 1, 0, 0, 0 }) task(Role.PILOT, 2, pulse);
    }

    private void move(final ServerPlayer actor, final BlockPos position) {
        actor.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, Set.of(), 0, 0, true);
        actor.setDeltaMovement(Vec3.ZERO);
    }

    private void advance(final int second) {
        final ScenarioMission mission = controller.mission();
        // Run the actual controller's tick callback without waiting 150 seconds of wall time per scenario.
        for (int tick = 0; tick < 3200 && mission.elapsed < second && mission.phase == ScenarioMission.Phase.ACTIVE; tick++)
            ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
        context.assertTrue(mission.elapsed == second, "Scenario stopped early: " + mission.id + "/" + mission.elapsed + ": " + mission.feedback);
    }

    private void finish() {
        final ScenarioMission mission = controller.mission();
        context.assertTrue(mission.phase == ScenarioMission.Phase.SUCCESS, "Scenario did not reach full success: " + mission.id + ": " + mission.feedback);
        System.out.println("Full world scenario success: " + mission.id + " at " + mission.elapsed + "s / " + mission.feedback);
        controller.end(server);
    }
}
