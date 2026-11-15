package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.vehicle.MarsRover;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

public final class RoverSystemsChecks {
    private RoverSystemsChecks() {
        /* w */
    }

    public static void run(final ClientGameTestContext context, final TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final boolean wasOperator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
            server.getPlayerList().op(new NameAndId(player.getGameProfile()));
            final MarsRover rover = player.level().getEntitiesOfClass(MarsRover.class, new AABB(18, 64, 17, 40, 75, 42))
                    .stream().findFirst().orElseThrow();
            player.stopRiding();
            rover.snapTo(28.5, 66, 25.5, 0, 0);
            player.teleportTo(player.level(), 30.5, 66, 25.5, Set.of(), 90, 0, true);
            final ItemStack labeled = new ItemStack(ModContent.MARS_SAMPLE);
            labeled.set(DataComponents.CUSTOM_NAME, Component.literal("Unique labeled sample"));
            player.setItemInHand(InteractionHand.MAIN_HAND, labeled);
            rover.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (rover.cargoCount() != 0 || !player.getMainHandItem().has(DataComponents.CUSTOM_NAME))
                throw new AssertionError("A standardized rack consumed a specimen's custom data");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.MARS_SAMPLE, 4));
            final int sampleCount = player.getInventory().countItem(ModContent.MARS_SAMPLE);
            for (int sample = 0; sample < 3; sample++)
                rover.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            rover.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (rover.cargoCount() != 3 || player.getMainHandItem().getCount() != 1)
                throw new AssertionError("Cargo overflow duplicated or destroyed a real sample");
            final var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            rover.saveWithoutId(saved);
            final var tag = saved.buildResult();
            tag.putFloat("RoverEnergy", 0);
            rover.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag));
            if (rover.battery() != 0 || rover.cargoCount() != 3)
                throw new AssertionError("Persistent vehicle resources were not restored");
            player.startRiding(rover);
            player.setLastClientInput(new Input(true, false, false, false, false, false, false));
            final Vec3 before = rover.position();
            for (int tick = 0; tick < 20; tick++) rover.tick();
            if (Math.pow(rover.getX() - before.x, 2) + Math.pow(rover.getZ() - before.z, 2) > 0.00001)
                throw new AssertionError("An empty rover battery still supplied driving thrust");
            player.setLastClientInput(Input.EMPTY);
            player.stopRiding();
            player.setShiftKeyDown(true);
            rover.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (rover.cargoCount() != 0 || player.getInventory().countItem(ModContent.MARS_SAMPLE) != sampleCount)
                throw new AssertionError("Unloading cargo lost or duplicated samples");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.ENGINEERING_TOOL));
            final MissionController controller = MissionController.get(server);
            final double readyBattery = controller.snapshot().battery();
            rover.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (rover.battery() != 25 || controller.snapshot().battery() != readyBattery)
                throw new AssertionError("Ready-state rover charging was not a free rehearsal");
            controller.control(server.createCommandSourceStack(), "debug_start");
            final double activeBattery = controller.snapshot().battery();
            rover.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (rover.battery() != 50 || controller.snapshot().battery() != activeBattery - 5)
                throw new AssertionError("Rover charging did not transfer finite shared power");
            controller.control(server.createCommandSourceStack(), "pause");
            rover.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (rover.battery() != 50)
                throw new AssertionError("A paused charging request changed rover energy");
            controller.control(server.createCommandSourceStack(), "reset");
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!wasOperator) server.getPlayerList().deop(new NameAndId(player.getGameProfile()));
        });
        System.out.println("Rover systems checks passed: finite cargo, persistent energy, zero-energy stop and shared charging costs.");
    }
}
