package cc.nimbusnova.mc.server;

import cc.nimbusnova.mc.hub.MissionHub;
import cc.nimbusnova.mc.learning.LearningChecks;
import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.content.OutpostContent;
import cc.nimbusnova.mc.content.ShipContent;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.ship.ShipActionPayload;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.ship.ShipTask;
import cc.nimbusnova.mc.ship.rooms.ShipRooms;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.ship.ShipMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Set;

public final class MissionServerGameTests {
    @GameTest(maxTicks = 400)
    public void worldsAndCooperativePhysicalRepairs(final GameTestHelper context) throws IOException {
        final var server = context.getLevel().getServer();
        ShipMap.create(server);
        final ServerLevel ship = server.getLevel(ShipMap.DIMENSION);
        if (ship == null) throw new AssertionError("The ship dimension was not loaded");

        for (final ShipMap.Marker marker : ShipMap.MARKERS)
            context.assertTrue(ship.getBlockState(marker.position()).is(ShipMap.block(marker.task())), "Imported station missing: " + marker);

        context.assertTrue(ship.getBlockState(ShipMap.SPAWN.below()).isFaceSturdy(ship, ShipMap.SPAWN.below(), Direction.UP), "Bridge spawn has no supporting floor");
        final BlockPos playerChange = new BlockPos(0, 80, 22);
        final var original = ship.getBlockState(playerChange);
        ship.setBlock(playerChange, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        ShipMap.create(server);
        context.assertTrue(ship.getBlockState(playerChange).is(Blocks.GOLD_BLOCK), "Import overwrote a player change");
        ship.setBlock(playerChange, original, Block.UPDATE_CLIENTS);
        context.assertTrue(ship.getBlockState(ShipMap.MARKERS.getFirst().position()).is(ShipContent.COMMS_ARRAY), "Repeat import changed equipment");

        MarsTrainingMap.create(server.createCommandSourceStack());
        final ServerLevel mars = server.getLevel(MarsTrainingMap.DIMENSION);
        context.assertTrue(mars != null && mars.getBlockState(MarsTrainingMap.SAMPLE_ANALYZER).is(OutpostContent.SAMPLE_ANALYZER), "Mars equipment did not import");
        MarsTrainingMap.create(server.createCommandSourceStack());

        final ServerPlayer engineer = context.makeMockServerPlayerInLevel();
        final ServerPlayer lifeSupport = context.makeMockServerPlayerInLevel();
        server.getPlayerList().deop(new NameAndId(engineer.getGameProfile()));
        server.getPlayerList().deop(new NameAndId(lifeSupport.getGameProfile()));
        context.assertFalse(engineer.getUUID().equals(lifeSupport.getUUID()), "Cooperative actors must have different identities");
        context.assertFalse(engineer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || lifeSupport.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "Role checks require ordinary crew actors");
        final ShipController controller = ShipController.get(server);
        controller.teacherAction(server, "ship_select");
        controller.action(engineer, new ShipActionPayload("role", BlockPos.ZERO, controller.mission().revision(), MoonMission.Role.POWER.ordinal()));
        controller.action(lifeSupport, new ShipActionPayload("role", BlockPos.ZERO, controller.mission().revision(), MoonMission.Role.LIFE_SUPPORT.ordinal()));
        context.assertTrue(controller.role(engineer) == MoonMission.Role.POWER && controller.role(lifeSupport) == MoonMission.Role.LIFE_SUPPORT,
                "Server actors could not claim different roles through the validated handler");

        final BlockPos storage = ShipMap.target(ShipTask.STORAGE, ShipMap.SPAWN);
        move(engineer, ship, storage.offset(0, 0, 1));
        ShipRooms.claimSupplies(engineer);
        move(lifeSupport, ship, storage.offset(0, 0, 1));
        ShipRooms.claimSupplies(lifeSupport);
        context.assertTrue(count(engineer, RepairContent.WIRE_KIT) == 2 && count(engineer, RepairContent.PATCH_PLATE.asItem()) == 4
                && count(lifeSupport, RepairContent.WIRE_KIT) == 0, "The shared repair pool was missing or duplicated across actors");

        ShipRooms.trigger(server, "ENGINEERING", "POWER");
        final BlockPos wire = ShipRooms.wirePosition();
        move(engineer, ship, wire.offset(-2, -1, 0));
        final int revision = controller.mission().revision();
        controller.action(engineer, new ShipActionPayload("task", wire, revision, 0));
        context.assertTrue(controller.mission().revision() == revision && !ShipRooms.state(server).powered(ShipRoomState.Room.ENGINEERING),
                "Unpowered equipment still operated");

        transfer(engineer, lifeSupport, RepairContent.WIRE_KIT, 1);
        equip(lifeSupport, RepairContent.WIRE_KIT);
        move(lifeSupport, ship, wire.offset(-2, -1, 0));
        use(lifeSupport, wire, Direction.WEST);
        context.assertTrue(!ShipRooms.state(server).powered(ShipRoomState.Room.ENGINEERING)
                && count(lifeSupport, RepairContent.WIRE_KIT) == 1, "A life-support actor bypassed engineering repair permissions");
        equip(engineer, RepairContent.WIRE_KIT);
        use(engineer, wire, Direction.WEST);
        context.assertTrue(ShipRooms.state(server).powered(ShipRoomState.Room.ENGINEERING)
                && count(engineer, RepairContent.WIRE_KIT) == 0, "Engineering repair did not restore power and consume the kit");

        final BlockPos breach = ShipRooms.breachPosition();
        final BlockState hull = ship.getBlockState(breach), outerHull = ship.getBlockState(breach.east());
        ShipRooms.trigger(server, "SCIENCE", "BREACH");
        for (int second = 0; second < 3; second++)
            ShipRooms.tick(server, false);
        context.assertTrue(ship.getBlockState(breach).isAir() && ship.getBlockState(breach.east()).isAir() && ShipRooms.state(server).pressure(ShipRoomState.Room.SCIENCE) == 70,
                "The breach did not create a real hole and pressure loss");
        context.assertTrue(Boolean.FALSE.equals(ShipRooms.doorOverride(ship, new BlockPos(3, 76, -18))), "The breached room did not isolate its door");
        context.assertFalse(Boolean.FALSE.equals(ShipRooms.doorOverride(ship, new BlockPos(3, 81, -19))), "A lower-deck breach isolated an unrelated upper-deck door");

        transfer(engineer, lifeSupport, RepairContent.PATCH_PLATE.asItem(), 4);
        equip(lifeSupport, RepairContent.PATCH_PLATE.asItem());
        move(lifeSupport, ship, breach.offset(-1, -1, 0));
        use(lifeSupport, breach.below(), Direction.UP);
        context.assertTrue(ship.getBlockState(breach).is(RepairContent.PATCH_PLATE)
                && count(lifeSupport, RepairContent.PATCH_PLATE.asItem()) == 3, "Life support did not place and consume a real repair plate");
        ShipRooms.tick(server, false);
        ShipRooms.tick(server, false);
        context.assertTrue(ShipRooms.state(server).pressure(ShipRoomState.Room.SCIENCE) == 80 && !ShipRooms.hasUnresolved(server),
                "A patched room did not recover safe pressure");
        ShipRooms.recoverOwnedBreach(server);
        context.assertTrue(ship.getBlockState(breach).equals(hull) && ship.getBlockState(breach.east()).equals(outerHull), "Owned-cell recovery did not restore the original hull");
        server.getPlayerList().op(new NameAndId(engineer.getGameProfile()));
        move(engineer, ship, wire.offset(-2, -1, 0));
        for (int socket = 0; socket < 4; socket++) {
            final int cartridge = controller.mission().aux(ShipTask.ENGINE) >> socket * 4 & 15;
            controller.action(engineer, new ShipActionPayload("task", wire, controller.mission().revision(), socket * 4 + cartridge - 1));
        }
        context.assertTrue(controller.mission().completed(ShipTask.ENGINE), "Engine setup rejected at " + engineer.position() + "; op=" + engineer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) + "; board=" + controller.mission().board(ShipTask.ENGINE));
        move(engineer, ship, storage.west());
        controller.action(engineer, new ShipActionPayload("task", storage, controller.mission().revision(), 0));
        controller.action(engineer, new ShipActionPayload("task", storage, controller.mission().revision(), 2));
        context.assertTrue(controller.mission().board(ShipTask.STORAGE) == 17, "Cargo placement rejected at " + engineer.position() + "; board=" + controller.mission().board(ShipTask.STORAGE));
        controller.action(engineer, new ShipActionPayload("hold_begin", storage, controller.mission().revision(), 3));
        context.runAfterDelay(25, () -> {
            context.assertTrue(engineer.level() == ship && engineer.distanceToSqr(Vec3.atCenterOf(storage)) < 36, "Cargo actor moved out of reach while holding: " + engineer.position());
            controller.action(engineer, new ShipActionPayload("hold_finish", storage, controller.mission().revision(), 3));
            context.assertTrue(controller.mission().ready(), "Physical power and cargo setup did not enable launch");
            controller.teacherAction(server, "ship_start");
            final var launched = controller.mission().snapshot();
            controller.teacherAction(server, "ship_start");
            context.assertTrue(controller.mission().snapshot().equals(launched), "A repeated launch changed an already launched attempt");
            boolean skippedLearning = false;
            try {
                controller.teacherAction(server, "ship_resume");
            } catch (final IllegalStateException expected) {
                skippedLearning = true;
            }
            context.assertTrue(skippedLearning, "Teacher resume bypassed the crew reading check");
            final var check = LearningChecks.snapshot(server, "ship", false);
            LearningChecks.applyAnswer(engineer, "ship", check.revision(), 0);
            context.assertFalse(LearningChecks.snapshot(server, "ship", false).solved(), "A wrong docking prediction passed the reading check");
            LearningChecks.applyAnswer(engineer, "ship", check.revision(), 1);
            controller.teacherAction(server, "ship_acknowledge");
            controller.teacherAction(server, "ship_resume");
            final var active = controller.mission().snapshot();
            controller.teacherAction(server, "ship_start");
            context.assertTrue(controller.mission().snapshot().equals(active), "Repeated active launch reset the mission");
            controller.teacherAction(server, "ship_end");

            final MissionHub hub = MissionHub.get(server);
            for (final String id : new String[] { "mars", "moon", "ship", "shelter", "long_night", "greenhouse" }) {
                hub.action(server, "select", id);
                context.assertTrue(hub.selected(server).equals(id), "Unified hub did not select " + id);
                final var state = hub.state(server).deepCopy();
                hub.action(server, "select", id);
                context.assertTrue(hub.state(server).equals(state), "Repeated selection reset " + id);
            }
            boolean invalidRejected = false;
            try {
                hub.action(server, "select", "missing_mission");
            } catch (final IllegalArgumentException expected) {
                invalidRejected = true;
            }
            context.assertTrue(invalidRejected && hub.selected(server).equals("greenhouse"), "Invalid mission selection changed the running mission");
            hub.action(server, "end", "");
            server.getPlayerList().remove(engineer);
            server.getPlayerList().remove(lifeSupport);
            hub.action(server, "select", "mars");
            context.assertFalse(hub.state(server).get("ready").getAsBoolean(), "An empty classroom was marked ready");
            hub.action(server, "end", "");
            System.out.println("Headless world checks passed: six map selections, preserved player edits, two role-bound server actors, physical repairs, timed cargo and idempotent launch.");
            context.succeed();
        });
    }

    private static void move(final ServerPlayer player, final ServerLevel level, final BlockPos position) {
        player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, Set.of(), 0, 0, true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void use(final ServerPlayer player, final BlockPos position, final Direction face) {
        UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position).add(face.getUnitVec3().scale(0.5)), face, position, false));
    }

    private static int count(final ServerPlayer player, final Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void transfer(final ServerPlayer from, final ServerPlayer to, final Item item, final int amount) {
        for (int slot = 0; slot < from.getInventory().getContainerSize(); slot++) {
            if (!from.getInventory().getItem(slot).is(item)) continue;
            final ItemStack transferred = from.getInventory().removeItem(slot, amount);
            if (transferred.getCount() != amount || !to.addItem(transferred)) throw new AssertionError("Repair supply transfer failed");
            return;
        }
        throw new AssertionError("No repair supplies to transfer");
    }

    private static void equip(final ServerPlayer player, final Item item) {
        if (player.getMainHandItem().is(item)) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (!player.getInventory().getItem(slot).is(item)) continue;
            final ItemStack previous = player.getMainHandItem().copy();
            final ItemStack stack = player.getInventory().removeItem(slot, 64);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            if (!previous.isEmpty()) player.addItem(previous);
            return;
        }
        throw new AssertionError("No repair item to equip");
    }
}
