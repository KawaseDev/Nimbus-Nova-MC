package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.client.ship.ShipClient;
import cc.nimbusnova.mc.ship.rooms.ShipRooms;
import cc.nimbusnova.mc.ship.rooms.RoomActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.level.block.DoorBlock;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState;
import cc.nimbusnova.mc.client.ship.rooms.RoomScreen;
import cc.nimbusnova.mc.client.ship.rooms.RoomClient;
import cc.nimbusnova.mc.world.ship.ShipMap;
import cc.nimbusnova.mc.ship.ShipTask;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class RoomEmergencyChecks {
    private RoomEmergencyChecks() {
        /* w */
    }

    private static void collect(final ClientGameTestContext context, final TestSingleplayerContext world) {
        context.setScreen(() -> null);
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p 9 76 14 180 15");
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final BlockPos storage = ShipMap.target(ShipTask.STORAGE, player.blockPosition());
            player.setShiftKeyDown(true);
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(storage), Direction.UP, storage, false));
            player.setShiftKeyDown(false);
            if (count(player, RepairContent.WIRE_KIT) != 2 || count(player, RepairContent.PATCH_PLATE.asItem()) != 4)
                throw new AssertionError("Storage did not issue the finite repair supplies");
            ShipRooms.claimSupplies(player);
            if (count(player, RepairContent.WIRE_KIT) != 2)
                throw new AssertionError("Storage duplicated repair supplies");
        });
    }

    static void doors(final ClientGameTestContext context, final TestSingleplayerContext world) {
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p 4 76 -18 90 0");
        final BlockPos door = new BlockPos(3, 76, -18);
        context.waitFor(client -> client.level != null && client.level.getBlockState(door).getValue(DoorBlock.OPEN));
        context.runOnClient(client -> ClientPlayNetworking.send(new RoomActionPayload("SCIENCE", "close")));
        context.waitFor(client -> client.level != null && !client.level.getBlockState(door).getValue(DoorBlock.OPEN));
        context.runOnClient(client -> ClientPlayNetworking.send(new RoomActionPayload("SCIENCE", "open")));
        context.waitFor(client -> client.level != null && client.level.getBlockState(door).getValue(DoorBlock.OPEN));
        context.runOnClient(client -> ClientPlayNetworking.send(new RoomActionPayload("SCIENCE", "auto")));
        System.out.println("Room door checks passed: proximity, manual close, manual open and automatic mode.");
    }

    static void repairPower(final ClientGameTestContext context, final TestSingleplayerContext world) {
        context.waitFor(_ -> ShipClient.state() != null && ShipClient.state().title().equals("Repair stores")
                && ShipClient.state().target().equals(ShipMap.target(ShipTask.STORAGE, ShipMap.SPAWN)));
        collect(context, world);
        context.waitFor(_ -> ShipClient.state() != null && ShipClient.state().title().equals("Engineering cable")
                && ShipClient.state().target().equals(ShipRooms.wirePosition()));
        context.setScreen(() -> null);
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p -11 81 -38 -90 0");
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            if (ShipRooms.state(server).powered(ShipRoomState.Room.ENGINEERING))
                throw new AssertionError("The authored power fault did not stop engineering");
            if (!ShipRooms.blockedStation(player, ShipRooms.wirePosition()))
                throw new AssertionError("An unpowered station still accepted normal operation");
        });
        world.getServer().runCommand("execute as @p run ship rooms");
        context.waitForScreen(RoomScreen.class);
        GuidedMissionChecks.capture(context, "room-engineering-power-alert");
        context.setScreen(() -> null);
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            equip(player, RepairContent.WIRE_KIT);
            final BlockPos wire = ShipRooms.wirePosition();
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(wire), Direction.WEST, wire, false));
            if (!ShipRooms.state(server).powered(ShipRoomState.Room.ENGINEERING) || count(player, RepairContent.WIRE_KIT) != 1)
                throw new AssertionError("A wire repair did not restore power and consume one real kit");
        });
    }

    static void patchHull(final ClientGameTestContext context, final TestSingleplayerContext world) {
        context.waitFor(_ -> ShipClient.state() != null && ShipClient.state().title().equals("Science hull breach")
                && ShipClient.state().target().equals(ShipRooms.breachPosition()));
        context.setScreen(() -> null);
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p 10 76 -12 -90 0");
        context.waitFor(client -> sciencePressure() <= 70);
        world.getServer().runCommand("execute as @p run ship rooms");
        context.waitForScreen(RoomScreen.class);
        GuidedMissionChecks.capture(context, "room-science-pressure-alert");
        context.setScreen(() -> null);
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final BlockPos breach = ShipRooms.breachPosition();
            if (!player.level().getBlockState(breach).isAir())
                throw new AssertionError("The scenario did not create a physical hull hole");
            equip(player, RepairContent.PATCH_PLATE.asItem());
            final BlockPos supporting = breach.below();
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(supporting).add(0, 0.5, 0), Direction.UP, supporting, false));
            if (!player.level().getBlockState(breach).is(RepairContent.PATCH_PLATE) || count(player, RepairContent.PATCH_PLATE.asItem()) != 3)
                throw new AssertionError("A physical patch did not seal the hole and consume a plate");
        });
        context.waitFor(client -> sciencePressure() >= 80);
        world.getServer().runOnServer(server -> {
            if (ShipRooms.hasUnresolved(server))
                throw new AssertionError("The repaired room did not recover safe pressure");
        });
        GuidedMissionChecks.capture(context, "room-patched-hull");
        System.out.println("Room emergency checks passed: finite storage, disabled power, physical wire repair, real hull patch and pressure recovery.");
    }

    private static int sciencePressure() {
        final var state = RoomClient.state();
        if (state == null) return 100;
        return state.getAsJsonArray("rooms").asList().stream().map(element -> element.getAsJsonObject())
                .filter(room -> room.get("id").getAsString().equals("SCIENCE"))
                .findFirst().orElseThrow().get("pressure").getAsInt();
    }

    private static int count(final ServerPlayer player, final Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void equip(final ServerPlayer player, final Item item) {
        if (player.getMainHandItem().is(item)) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (!player.getInventory().getItem(slot).is(item)) continue;
            final ItemStack previous = player.getMainHandItem().copy();
            final ItemStack stack = player.getInventory().removeItem(slot, 64);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            if (!previous.isEmpty()) player.getInventory().add(previous);
            return;
        }
        throw new AssertionError("The crew did not collect " + item);
    }
}
