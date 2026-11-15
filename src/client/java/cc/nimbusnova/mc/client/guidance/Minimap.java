package cc.nimbusnova.mc.client.guidance;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.mission.MissionProgressPayload;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import cc.nimbusnova.mc.world.ship.ShipMap;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.awt.Color;

/** A floor-plan style local map: walls at head height are dark, walkable floor is bright, so it reads indoors and outdoors. */
public final class Minimap {
    private static final int RADIUS = 20, CELL = 2, SIZE = RADIUS * 2 + 1;
    private static final int[] COLORS = new int[SIZE * SIZE];
    private static final Color AMBER = new Color(255, 200, 87), CYAN = new Color(95, 225, 240);
    private static int centerX, centerZ, age;
    private static boolean visible = true, sampled;

    private Minimap() {
        /* w */
    }

    public static void register(final KeyMapping.Category category) {
        final KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nimbus_nova.minimap", InputConstants.KEY_M, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick())
                visible = !visible;

            if (!visible || client.level == null || client.player == null || !supported(client)) {
                sampled = false;
                return;
            }

            if (!sampled || age++ >= 3)
                sample(client);
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.parse("nimbus_nova:minimap"), (graphics, _) -> {
            final Minecraft client = Minecraft.getInstance();
            if (!visible || !sampled || client.player == null || client.level == null || client.gui.screen() != null
                    || client.gui.hud.isHidden() || !supported(client)) return;

            render(graphics, client);
        });
    }

    private static boolean supported(final Minecraft client) {
        final var dimension = client.level.dimension();
        return dimension.equals(MarsTrainingMap.DIMENSION) || dimension.equals(ShipMap.DIMENSION) || dimension.equals(LunarSurface.DIMENSION)
                || dimension.identifier().toString().startsWith("nimbus_nova:scenario_");
    }

    private static void sample(final Minecraft client) {
        final BlockPos origin = client.player.blockPosition();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        centerX = origin.getX();
        centerZ = origin.getZ();
        age = 0;
        sampled = true;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                final int x = centerX + column - RADIUS, z = centerZ + row - RADIUS;
                cursor.set(x, origin.getY() + 1, z);
                final BlockState head = client.level.getBlockState(cursor);
                int color = head.isSolid() ? head.getMapColor(client.level, cursor).col : 0;
                if (color != 0) {
                    COLORS[row * SIZE + column] = shade(color, 0.42F);
                    continue;
                }

                for (int depth = 0; depth <= 10 && color == 0; depth++) {
                    cursor.set(x, origin.getY() - depth, z);
                    final BlockState state = client.level.getBlockState(cursor);
                    if (!state.isAir())
                        color = shade(state.getMapColor(client.level, cursor).col, 0.92F - depth * 0.05F);
                }

                COLORS[row * SIZE + column] = color;
            }
        }
    }

    private static int shade(final int color, final float brightness) {
        if (color == 0) return 0;
        return 0xFF000000 | Math.round((color >> 16 & 255) * brightness) << 16 | Math.round((color >> 8 & 255) * brightness) << 8
                | Math.round((color & 255) * brightness);
    }

    private static void render(final GuiGraphicsExtractor graphics, final Minecraft client) {
        final int span = SIZE * CELL, left = graphics.guiWidth() - span - 10, top = 6;
        NovaRenderer.hud(graphics, left - 4, top, span + 8, span + 8, new Color(8, 11, 19));
        NovaRenderer.rect(graphics, left, top + 4, span, span, new Color(6, 9, 16));
        for (int row = 0; row < SIZE; row++) {
            int start = 0;
            for (int column = 1; column <= SIZE; column++) {
                if (column < SIZE && COLORS[row * SIZE + column] == COLORS[row * SIZE + start]) continue;
                if (COLORS[row * SIZE + start] != 0)
                    graphics.fill(left + start * CELL, top + 4 + row * CELL, left + column * CELL, top + 4 + (row + 1) * CELL, COLORS[row * SIZE + start]);

                start = column;
            }
        }

        final float originX = left + (RADIUS + 0.5F) * CELL, originY = top + 4 + (RADIUS + 0.5F) * CELL;
        for (final var other : client.level.players()) {
            if (other == client.player) continue;
            final float deltaX = (float) (other.getX() - centerX - 0.5), deltaZ = (float) (other.getZ() - centerZ - 0.5);
            if (Math.abs(deltaX) > RADIUS || Math.abs(deltaZ) > RADIUS) continue;
            NovaRenderer.rect(graphics, originX + deltaX * CELL - 2, originY + deltaZ * CELL - 2, 4, 4, new Color(5, 7, 13));
            NovaRenderer.rect(graphics, originX + deltaX * CELL - 1, originY + deltaZ * CELL - 1, 2, 2, CYAN);
        }

        final MissionProgressPayload objective = HologramGuidance.objective(client);
        if (objective != null && objective.hasWorldTarget()) {
            final float deltaX = objective.targetX() - centerX, deltaZ = objective.targetZ() - centerZ;
            final float reach = Math.max(Math.abs(deltaX), Math.abs(deltaZ)) / (RADIUS - 1F);
            final float markerX = originX + deltaX / Math.max(1, reach) * CELL, markerY = originY + deltaZ / Math.max(1, reach) * CELL;
            final Color marker = NovaRenderer.mix(AMBER, Color.WHITE, NovaRenderer.pulse(6) * 0.6F);
            NovaRenderer.rect(graphics, markerX - 3, markerY - 3, 7, 7, new Color(5, 7, 13));
            NovaRenderer.rect(graphics, markerX - 2, markerY - 2, 5, 5, marker);
            if (reach <= 1)
                NovaRenderer.rect(graphics, markerX - 1, markerY - 1, 3, 3, new Color(5, 7, 13));
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX + (float) (client.player.getX() - centerX - 0.5) * CELL,
                originY + (float) (client.player.getZ() - centerZ - 0.5) * CELL);
        graphics.pose().rotate((float) Math.toRadians(client.player.getYRot() + 180));
        for (int step = 0; step < 5; step++)
            NovaRenderer.rect(graphics, -step - 1, -4 + step * 2 - 1, step * 2 + 2, 3, new Color(5, 7, 13));

        for (int step = 0; step < 4; step++)
            NovaRenderer.rect(graphics, -step, -3 + step * 2, step * 2 + 1, 2, Color.WHITE);
        graphics.pose().popMatrix();

        NovaRenderer.rect(graphics, left + span / 2 - 4, top + 1, 9, 9, new Color(5, 7, 13));
        NovaRenderer.text(graphics, "N", left + span / 2 - 2, top + 2, 8, AMBER);
    }
}
