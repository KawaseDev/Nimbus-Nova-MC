package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.mission.MissionProgressPayload;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

final class ObjectiveGuidance {
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

        if (objective.stage().equals("SHELTER_SAFE") || objective.stage().equals("TEAM")) return objective.actionHint();
        if (objective.stage().equals("ROLE"))
            return NimbusNovaClient.consoleKey.getTranslatedKeyMessage().getString() + " / Crew / choose a role";

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
        final Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;

        final double deltaX = objective.targetX() + 0.5 - client.player.getX();
        final double deltaZ = objective.targetZ() + 0.5 - client.player.getZ();
        final double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        return Math.IEEEremainder(targetYaw - client.player.getYRot(), 360);
    }

    static void arrow(final GuiGraphicsExtractor graphics, final MissionProgressPayload objective,
                      final float x, final float y, final Color color) {
        if (objective == null || !onMars() || Minecraft.getInstance().player == null) return;
        if (objective.stage().equals("SHELTER_SAFE") || objective.stage().equals("ROLE") || objective.stage().equals("TEAM")) return;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().rotate((float) Math.toRadians(relativeYaw(objective)));
        NovaRenderer.rect(graphics, -0.8F, -5, 1.6F, 12, 0.7F, color);
        graphics.pose().translate(0, -5);
        graphics.pose().rotate((float) Math.PI / 4);
        NovaRenderer.rect(graphics, -0.7F, 0, 1.4F, 6, 0.5F, color);
        graphics.pose().rotate(-(float) Math.PI / 2);
        NovaRenderer.rect(graphics, -0.7F, 0, 1.4F, 6, 0.5F, color);
        graphics.pose().popMatrix();
    }
}
