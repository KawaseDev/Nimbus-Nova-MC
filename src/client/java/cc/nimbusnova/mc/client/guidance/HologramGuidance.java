package cc.nimbusnova.mc.client.guidance;

import cc.nimbusnova.mc.client.GuidedMissionClient;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.mission.MissionProgressPayload;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@NullMarked
public final class HologramGuidance {
    private static final List<Anchor> ANCHORS = List.of(
            new Anchor("Commander Vega", -3.5, 68.4, -3.5),
            new Anchor("Power relay", -23.5, 67.4, 0.5),
            new Anchor("Oxygen recycler", -4.5, 67.4, 3.5),
            new Anchor("Sample analyzer", 6.5, 67.5, 1.5),
            new Anchor("Crew supplies", 7.5, 67.5, -2.5),
            new Anchor("Weather station", 14.5, 67.5, -3.5),
            new Anchor("Mars sample", 40.5, 67.2, -15.5),
            new Anchor("Crew shelter", 0.5, 69, 20.5),
            new Anchor("Rover workshop", 28.5, 69, 25.5),
            new Anchor("Mission guide", -7.5, 67.4, -17.5),
            new Anchor("Habitat airlock", -2.5, 67.4, -12.5),
            new Anchor("Shelter entrance", -3.5, 67.4, 18.5));
    private static final Color CYAN = new Color(115, 230, 237);
    private static final Color AMBER = new Color(248, 193, 108);
    private static final Color BACKGROUND = new Color(11, 25, 33, 213);
    private static final Color TEXT = new Color(232, 247, 247);
    private static int particleTick;

    private HologramGuidance() {
        /* w */
    }

    public static void register() {
        verifyProjection();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.parse("nimbus_nova:hologram_guidance"), HologramGuidance::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (unavailable(client) || client.player == null || client.level == null || ++particleTick % 4 != 0 || client.options.particles().get() == ParticleStatus.MINIMAL) return;

            final MissionProgressPayload objective = GuidedMissionClient.progress();
            if (objective == null || !hasWorldTarget(objective)) return;

            final Vec3 target = new Vec3(objective.targetX() + 0.5, objective.targetY() + 0.15, objective.targetZ() + 0.5);
            if (client.player.distanceToSqr(target) > 48 * 48) return;

            final double angle = client.level.getGameTime() * 0.12;
            final int amount = client.options.particles().get() == ParticleStatus.ALL ? 2 : 1;
            for (int index = 0; index < amount; index++) {
                final double phase = angle + index * Math.PI;
                client.particleEngine.createParticle(ParticleTypes.END_ROD,
                        target.x + Math.cos(phase) * 0.65, target.y + 0.2, target.z + Math.sin(phase) * 0.65,
                        0, 0.018, 0);
            }
        });
    }

    private static boolean unavailable(final Minecraft client) {
        return client.level == null || client.player == null || client.gui.screen() != null || client.gui.hud.isHidden()
                || !client.level.dimension().equals(MarsTrainingMap.DIMENSION);
    }

    private static boolean hasWorldTarget(final MissionProgressPayload objective) {
        return switch (objective.stage()) {
            case "ARRIVAL", "ROLE", "TEAM", "PAUSED", "COMPLETE", "SHELTER_SAFE" -> false;
            default -> true;
        };
    }

    private static void render(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
        final Minecraft client = Minecraft.getInstance();
        if (unavailable(client) || client.level == null || client.player == null) return;

        final Camera camera = client.gameRenderer.mainCamera();
        final Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        final Vec3 eye = camera.position();
        final double time = client.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        final float motion = client.options.screenEffectScale().get().floatValue();
        final MissionProgressPayload objective = GuidedMissionClient.progress();
        final List<LabelBounds> occupied = new ArrayList<>();
        Anchor active = null;
        if (objective != null && hasWorldTarget(objective)) {
            final Vec3 point = new Vec3(objective.targetX() + 0.5, objective.targetY() + 1.4, objective.targetZ() + 0.5);
            active = ANCHORS.stream().min(Comparator.comparingDouble(anchor -> anchor.position().distanceToSqr(point)))
                    .filter(anchor -> anchor.position().distanceToSqr(point) < 40).orElse(null);
            final Anchor marker = active == null ? new Anchor(objective.title(), point.x, point.y, point.z) : active;
            draw(graphics, viewProjection, eye, marker, true, objective.actionHint(), time, motion, occupied);
        }

        final Anchor activeAnchor = active;
        ANCHORS.stream().filter(anchor -> anchor != activeAnchor && anchor.position().distanceToSqr(eye) < 20 * 20)
                .sorted(Comparator.comparingDouble(anchor -> anchor.position().distanceToSqr(eye))).limit(3)
                .forEach(anchor -> draw(graphics, viewProjection, eye, anchor, false, "", time, motion, occupied));
    }

    private static void draw(final GuiGraphicsExtractor graphics, final Matrix4fc viewProjection, final Vec3 eye,
                             final Anchor anchor, final boolean active, final String action, final double time,
                             final float motion, final List<LabelBounds> occupied) {
        final Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        final Vec3 point = anchor.position();
        final double distance = eye.distanceTo(point);
        if (distance > (active ? 96 : 20) || distance < 0.5) return;

        final Point screen = project(viewProjection, point.x - eye.x, point.y - eye.y, point.z - eye.z, graphics.guiWidth(), graphics.guiHeight());
        if (screen == null || screen.x < 12 || screen.x > graphics.guiWidth() - 12 || screen.y < 8 || screen.y > graphics.guiHeight() - 48) return;

        final boolean occluded = client.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, client.player)).getType() != HitResult.Type.MISS;
        if (occluded && !active) return;

        final float pulse = (float) (0.78 + Math.sin(time * 0.075) * 0.16 * motion);
        final Color color = active ? AMBER : CYAN;
        final float ring = active ? 11 + (float) Math.sin(time * 0.075) * motion : 5;
        if (screen.y >= 118) {
            NovaRenderer.glow(graphics, screen.x - ring, screen.y - ring, ring * 2, ring * 2, ring, active ? 5 : 2,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (active ? pulse * 65 : 30)));
            NovaRenderer.stroke(graphics, screen.x - ring, screen.y - ring, ring * 2, ring * 2, ring, 0.8F,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (pulse * 215)));
            if (active) {
                NovaRenderer.rect(graphics, screen.x - 1, screen.y - 4, 2, 8, 1, color);
                NovaRenderer.rect(graphics, screen.x - 4, screen.y - 1, 8, 2, 1, color);
            }
        }

        final String detail = active ? occluded ? "Follow the route to this marker" : distance <= 6 ? action : (int) Math.ceil(distance) + " blocks away" : "";
        final float fontSize = active ? 10 : 8.5F;
        final float width = Math.min(graphics.guiWidth() - 24, Math.max(NovaRenderer.width(anchor.name, fontSize), NovaRenderer.width(detail, 8)) + 20);
        final float height = active ? 34 : 22;
        final float left = Math.clamp(screen.x - width / 2, 12, graphics.guiWidth() - width - 12);
        float top = screen.y - height - ring - 5;
        if (top < 118) top = screen.y + ring + 5;
        if (top < 118 || top + height > graphics.guiHeight() - 48) return;

        final LabelBounds bounds = new LabelBounds(left, top, width, height);
        if (occupied.stream().anyMatch(bounds::overlaps)) return;
        occupied.add(bounds);
        NovaRenderer.rect(graphics, left, top, width, height, 6, BACKGROUND);
        NovaRenderer.stroke(graphics, left, top, width, height, 6, 0.6F,
                new Color(color.getRed(), color.getGreen(), color.getBlue(), active ? 155 : 80));
        NovaRenderer.text(graphics, anchor.name, left + 10, top + 3, fontSize, active ? color : TEXT);
        if (active)
            NovaRenderer.text(graphics, detail, left + 10, top + 19, 8, new Color(190, 218, 224));
    }

    private static @Nullable Point project(final Matrix4fc matrix, final double x, final double y, final double z,
                                           final int width, final int height) {
        final Vector4f clip = new Vector4f((float) x, (float) y, (float) z, 1).mul(matrix);
        if (!Float.isFinite(clip.w) || clip.w <= 0.001F) return null;

        final float screenX = (clip.x / clip.w + 1) * width * 0.5F;
        final float screenY = (1 - clip.y / clip.w) * height * 0.5F;
        return Float.isFinite(screenX) && Float.isFinite(screenY) ? new Point(screenX, screenY) : null;
    }

    private static void verifyProjection() {
        final Matrix4f perspective = new Matrix4f().perspective((float) Math.PI / 2, 1, 0.05F, 128);
        final Point center = project(perspective, 0, 0, -10, 200, 200);
        final Point right = project(perspective, 1, 0, -10, 200, 200);
        final Point up = project(perspective, 0, 1, -10, 200, 200);
        if (center == null || right == null || up == null || Math.abs(center.x - 100) > 0.01
                || Math.abs(center.y - 100) > 0.01 || right.x <= center.x || up.y >= center.y
                || project(perspective, 0, 0, 10, 200, 200) != null)
            throw new IllegalStateException("Hologram projection must preserve camera-facing screen directions");
    }

    private record Anchor(String name, double x, double y, double z) {
        Vec3 position() {
            return new Vec3(x, y, z);
        }
    }

    private record Point(float x, float y) {
        /* w */
    }

    private record LabelBounds(float x, float y, float width, float height) {
        boolean overlaps(final LabelBounds other) {
            return x < other.x + other.width + 5 && x + width + 5 > other.x
                    && y < other.y + other.height + 4 && y + height + 4 > other.y;
        }
    }
}
