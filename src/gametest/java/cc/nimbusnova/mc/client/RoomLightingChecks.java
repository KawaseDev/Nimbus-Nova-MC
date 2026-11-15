package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.ship.rooms.RoomLighting;
import cc.nimbusnova.mc.ship.rooms.ShipRooms;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState.Fault;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState.Room;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class RoomLightingChecks {
    private RoomLightingChecks() {
        /* w */
    }

    static void run(final ClientGameTestContext context, final TestSingleplayerContext world) {
        final Vec3 previous = world.getServer().computeOnServer(server -> world.getConnection().getServerPlayer().position());
        final BlockPos control = world.getServer().computeOnServer(server -> {
            RoomLighting.tick(server, true);
            if (RoomLighting.fixtureCount(server) == 0)
                throw new AssertionError("The ship has no managed cabin lights");
            final var level = world.getConnection().getServerLevel();
            final int normal = emission(level);
            ShipRooms.state(server).trigger(Room.ENGINEERING, Fault.POWER);
            RoomLighting.tick(server, false);
            final int emergency = emission(level);
            if (normal <= emergency || emergency <= 0)
                throw new AssertionError("Power loss did not dim the room while retaining escape markers");
            ShipRooms.state(server).repairWire(Room.ENGINEERING);
            RoomLighting.tick(server, false);
            if (emission(level) != normal)
                throw new AssertionError("Repairing power did not restore the lamps");
            return RoomLighting.switchPosition(server, Room.ENGINEERING);
        });
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p " + (control.getX() + 0.5)
                + " " + (control.getY() - 1) + " " + (control.getZ() + 0.5));
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final int normal = emission(player.level());
            player.setShiftKeyDown(false);
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(control), Direction.NORTH, control, false));
            if (emission(player.level()) >= normal)
                throw new AssertionError("The physical room switch did not turn cabin lighting off");
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(control), Direction.NORTH, control, false));
            if (emission(player.level()) != normal)
                throw new AssertionError("The physical room switch did not restore cabin lighting");
            player.level().setBlock(control, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            RoomLighting.clear(server);
            if (!player.level().getBlockState(control).is(Blocks.GOLD_BLOCK))
                throw new AssertionError("Lighting recovery overwrote a changed player block");
            player.level().setBlock(control, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
        world.getServer().runCommand("execute in nimbus_nova:return_window run tp @p " + previous.x + " " + previous.y + " " + previous.z);
        context.waitTicks(2);
        System.out.println("Room lighting checks passed: actual light reduction, backup markers, power repair, physical switch and player-block preservation.");
    }

    private static int emission(final ServerLevel level) {
        int total = 0;
        for (final BlockPos position : BlockPos.betweenClosed(-12, 80, -41, 12, 87, -22)) {
            final var state = level.getBlockState(position);
            if (state.is(Blocks.LIGHT)) total += state.getValue(BlockStateProperties.LEVEL);
            else if (state.is(RoomLighting.LAMP) && state.getValue(BlockStateProperties.LIT))
                total += state.getValue(BlockStateProperties.POWERED) ? 3 : 12;
        }
        return total;
    }
}
