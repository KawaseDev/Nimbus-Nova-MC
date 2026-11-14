package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.vehicle.MarsRover;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;

final class RoverVisibilityChecks {
    private RoverVisibilityChecks() {
        /* w */
    }

    static void captureDriverView(final ClientGameTestContext context, final TestSingleplayerContext world) {
        final CameraType previousCamera = context.computeOnClient(client -> client.options.getCameraType());
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            final MarsRover rover = player.level().getEntitiesOfClass(MarsRover.class, new AABB(18, 64, 17, 38, 70, 40))
                    .stream().findFirst().orElseThrow();
            player.setLastClientInput(Input.EMPTY);
            if (!player.startRiding(rover))
                throw new AssertionError("The driver could not board for the visibility check");
            player.setYRot(rover.getYRot());
            player.setXRot(0);
        });
        context.waitFor(client -> client.player != null && client.player.getVehicle() instanceof MarsRover);
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            if (client.player == null || !(client.player.getVehicle() instanceof MarsRover rover))
                throw new AssertionError("The rover passenger update did not reach the client");
            client.player.setYRot(rover.getYRot());
            client.player.setXRot(0);
        });
        context.waitTicks(10);
        GuidedMissionChecks.capture(context, "rover-first-person-forward");
        context.runOnClient(client -> client.options.setCameraType(previousCamera));
        world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().stopRiding());
    }
}
