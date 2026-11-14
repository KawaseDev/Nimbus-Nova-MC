package cc.nimbusnova.mc.client.effect;

import cc.nimbusnova.mc.client.render.NovaDustPipeline;
import cc.nimbusnova.mc.client.NimbusNovaClient;
import cc.nimbusnova.mc.effect.DustParticles;
import cc.nimbusnova.mc.effect.DustStormProfile;
import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class DustStormEffects {
    private static float intensity;
    private static ClientLevel previousLevel;

    private DustStormEffects() {
        /* w */
    }

    public static void register() {
        NovaDustPipeline.initialize();
        ParticleProviderRegistry.getInstance().register(DustParticles.STORM_CLOUD, DustCloudParticle.Provider::new);
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
            intensity = 0;
            previousLevel = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != previousLevel) {
                intensity = 0;
                previousLevel = client.level;
            }

            if (client.level == null || !client.level.dimension().equals(MarsTrainingMap.DIMENSION)) {
                intensity = 0;
                return;
            }

            final MissionSnapshotPayload packet = NimbusNovaClient.missionSnapshot();
            if (client.isPaused() || packet != null && packet.state().phase() == MissionState.Phase.PAUSED) return;

            final boolean storm = packet != null && packet.state().phase() == MissionState.Phase.ACTIVE && packet.state().storm();
            intensity = DustStormProfile.advance(intensity, storm, false);
            if (intensity <= 0) return;

            final int budget = switch (client.options.particles().get()) {
                case ALL -> 4;
                case DECREASED -> 2;
                case MINIMAL -> 0;
            };
            final Vec3 camera = client.gameRenderer.mainCamera().position();
            final RandomSource random = client.particleEngine.getRandom();
            for (int index = 0; index < Math.ceil(intensity * budget); index++) {
                final double x = camera.x + random.nextDouble() * 68 - 40;
                final double z = camera.z + random.nextDouble() * 68 - 34;
                final int blockX = Mth.floor(x), blockZ = Mth.floor(z);
                if (!client.level.hasChunk(blockX >> 4, blockZ >> 4)) continue;

                final double ground = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
                final double y = Math.max(ground + 0.6, camera.y - 2 + random.nextDouble() * 8);
                client.particleEngine.createParticle(DustParticles.STORM_CLOUD, x, y, z,
                        0.16 + random.nextDouble() * 0.1, 0.015 + random.nextDouble() * 0.02,
                        0.065 + random.nextDouble() * 0.055);
            }
        });
    }

    static float intensity() {
        return intensity;
    }

    static boolean paused() {
        final MissionSnapshotPayload packet = NimbusNovaClient.missionSnapshot();
        return Minecraft.getInstance().isPaused() || packet != null && packet.state().phase() == MissionState.Phase.PAUSED;
    }
}
