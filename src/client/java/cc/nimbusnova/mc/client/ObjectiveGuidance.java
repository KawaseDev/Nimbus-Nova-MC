package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.mission.MissionProgressPayload;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public final class ObjectiveGuidance {
    private ObjectiveGuidance() {
        /* w */
    }

    static boolean onMars() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.dimension().equals(MarsTrainingMap.DIMENSION);
    }

    static String navigation(final MissionProgressPayload objective) {
        if (!onMars()) return "Enter the Mars outpost first";
        if (Minecraft.getInstance().player == null || objective == null) return "Waiting for mission guidance";

        if (objective.stage().equals("ROLE"))
            return NimbusNovaClient.consoleKey.getTranslatedKeyMessage().getString() + " / Crew / choose a role";
        if (!objective.hasWorldTarget()) return objective.actionHint();

        final double deltaX = objective.targetX() + 0.5 - Minecraft.getInstance().player.getX();
        final double deltaY = objective.targetY() - Minecraft.getInstance().player.getY();
        final double deltaZ = objective.targetZ() + 0.5 - Minecraft.getInstance().player.getZ();
        final int distance = (int) Math.ceil(Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
        if (distance <= 5) return objective.actionHint();

        return turnLabel(relativeYaw(objective)) + " / " + distance + " blocks";
    }

    static String turnLabel(final double relativeYaw) {
        return Math.abs(relativeYaw) < 25 ? "Ahead" : Math.abs(relativeYaw) > 145 ? "Turn around"
                : relativeYaw < 0 ? "To your left" : "To your right";
    }

    static double relativeYaw(final MissionProgressPayload objective) {
        return relativeYaw(objective.targetX() + 0.5, objective.targetZ() + 0.5);
    }

    private static double relativeYaw(final double x, final double z) {
        final Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;

        final double deltaX = x - client.player.getX(), deltaZ = z - client.player.getZ();
        final double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        return Math.IEEEremainder(targetYaw - client.player.getYRot(), 360);
    }

    static void arrow(final GuiGraphicsExtractor graphics, final MissionProgressPayload objective,
                      final float x, final float y, final Color color) {
        if (objective == null || !objective.hasWorldTarget() || !onMars() || Minecraft.getInstance().player == null) return;

        drawArrow(graphics, relativeYaw(objective), x, y, color);
    }

    public static void waypointHint(final GuiGraphicsExtractor graphics, final BlockPos target, final float x, final float y, final Color color) {
        final Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        final int distance = (int) Math.ceil(client.player.position().distanceTo(Vec3.atCenterOf(target)));
        final int vertical = target.getY() - client.player.blockPosition().getY();
        final String level = vertical > 2 ? " / above" : vertical < -2 ? " / below" : "";
        final double yaw = relativeYaw(target.getX() + 0.5, target.getZ() + 0.5);
        drawArrow(graphics, yaw, x + 5, y + 5, color);
        NovaRenderer.text(graphics, turnLabel(yaw) + " / " + distance + " blocks" + level, x + 17, y, 8, color);
    }

    private static void drawArrow(final GuiGraphicsExtractor graphics, final double yaw, final float x, final float y, final Color color) {
        NovaRenderer.chamfer(graphics, x - 7, y - 7, 15, 15, 4, new Color(5, 7, 13, 210));
        NovaRenderer.stroke(graphics, x - 5, y - 7, 11, 1, 1, NovaRenderer.alpha(color, 90));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 0.5F, y + 0.5F);
        graphics.pose().rotate((float) Math.toRadians(yaw));
        NovaRenderer.rect(graphics, -1, -2, 2, 7, color);
        for (int row = 0; row < 4; row++)
            NovaRenderer.rect(graphics, -row - 1, -5 + row, row * 2 + 2, 1, color);
        graphics.pose().popMatrix();
    }
}
