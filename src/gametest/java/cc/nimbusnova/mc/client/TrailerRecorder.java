package cc.nimbusnova.mc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.mixin.client.gametest.screenshot.DeltaTrackerDefaultValueAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Saves 120 fps frame sequences for the project trailer when the run is started with -Ptrailer. */
final class TrailerRecorder {
    private static final boolean ENABLED = Boolean.getBoolean("nimbusnova.trailer");
    private static final int WIDTH = 1920, HEIGHT = 1080, FRAMES_PER_TICK = 6;
    private static final Set<String> SCENES = Set.of("dome-exterior", "dome-interior", "aster-rover", "ship-engine-waypoint-behind",
            "ship-sealed-panel-seams", "moon-surface", "moon-surface-overview", "scenario-shelter-overview",
            "scenario-long_night-overview", "scenario-greenhouse-overview", "scenario-greenhouse-site");
    private static final Set<String> GAMES = Set.of("power", "oxygen", "pilot-docking", "pilot-navigation", "pilot-descent",
            "pilot-orbit", "pilot-return", "comms-docking", "science-preparation", "engine-emergency");
    private static final Set<String> RECORDED = new HashSet<>();
    private static final Map<String, Integer> PLAYED = new HashMap<>(), FRAMES = new HashMap<>();

    private TrailerRecorder() {
        /* w */
    }

    static void scene(final ClientGameTestContext context, final String name) {
        if (!ENABLED || !SCENES.contains(name) || !RECORDED.add(name)) return;

        final boolean falling = context.computeOnClient(client -> client.gui.screen() == null && !client.player.isNoGravity());
        if (falling)
            context.runOnClient(client -> client.player.setNoGravity(true));

        record(context, "clip-" + name, 26, true);
        if (falling)
            context.runOnClient(client -> client.player.setNoGravity(false));
    }

    static void play(final ClientGameTestContext context, final String label) {
        if (ENABLED && GAMES.contains(label) && PLAYED.merge(label, 1, Integer::sum) <= 6)
            record(context, "play-" + label, 4, false);
    }

    private static void record(final ClientGameTestContext context, final String name, final int ticks, final boolean moving) {
        final int[] previous = context.computeOnClient(client -> {
            final int[] size = { client.getWindow().getWidth(), client.getWindow().getHeight(), client.options.guiScale().get() };
            resize(client, WIDTH, HEIGHT, 3);
            return size;
        });
        context.waitTicks(2);

        for (int tick = 0; tick < ticks; tick++) {
            context.runOnClient(client -> {
                if (moving && client.gui.screen() == null) {
                    final float yaw = client.player.getYRot() - 1.0F;
                    client.player.setYRot(yaw);
                    client.player.setDeltaMovement(Mth.cos(yaw * Mth.DEG_TO_RAD) * 0.2, 0, Mth.sin(yaw * Mth.DEG_TO_RAD) * 0.2);
                }

                for (int frame = 0; frame < FRAMES_PER_TICK; frame++) {
                    final var delta = DeltaTrackerDefaultValueAccessor.create(frame / (float) FRAMES_PER_TICK);
                    client.gameRenderer.update(delta);
                    client.gameRenderer.extract(delta, true);
                    client.gameRenderer.render();
                    RenderSystem.getDevice().createCommandEncoder().submit();

                    final File target = new File(client.gameDirectory, "trailer/" + name + "/" + String.format("%05d", FRAMES.merge(name, 1, Integer::sum)) + ".png");
                    target.getParentFile().mkdirs();
                    Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> Util.ioPool().execute(() -> {
                        try (image) {
                            image.writeToFile(target);
                        } catch (final IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }));
                }
            });
            context.waitTick();
        }

        context.waitTicks(3);
        context.runOnClient(client -> resize(client, previous[0], previous[1], previous[2]));
        context.waitTicks(2);
    }

    private static void resize(final Minecraft client, final int width, final int height, final int guiScale) {
        client.options.guiScale().set(guiScale);
        client.getWindow().setWidth(width);
        client.getWindow().setHeight(height);
        client.resizeGui();
        client.gameRenderer.resize(width, height);
    }
}
