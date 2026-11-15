package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.learning.LearningClient;
import cc.nimbusnova.mc.client.learning.LearningScreen;
import cc.nimbusnova.mc.learning.LearningChecks;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

final class LearningGameChecks {
    private LearningGameChecks() {
        /* w */
    }

    static int supported(final String incident) {
        return switch (incident) {
            case "SOLAR", "DUST", "DOCKING", "SURFACE" -> 1;
            case "RELAY", "DESCENT", "FILTER" -> 2;
            case "EMERGENCY", "RESTART" -> 0;
            default -> throw new AssertionError("Unexpected applied check: " + incident);
        };
    }

    static void solveOnServer(final ServerPlayer player, final String source) {
        final var packet = LearningChecks.snapshot(player.level().getServer(), source, false);
        if (packet.incident().isEmpty()) return;
        LearningChecks.applyAnswer(player, source, packet.revision(), packet.incident().equals("DUST")
                ? packet.firstValue() > packet.secondValue() ? 0 : packet.firstValue() < packet.secondValue() ? 1 : 2
                : supported(packet.incident()));
        LearningChecks.requireSolved(player.level().getServer(), source);
    }

    static void solve(final ClientGameTestContext context, final TestSingleplayerContext world, final String source) {
        world.getServer().runOnServer(server -> LearningChecks.request(world.getConnection().getServerPlayer(), source));
        context.waitForScreen(LearningScreen.class);
        final String incident = context.computeOnClient(client -> ((LearningScreen) client.gui.screen()).state().incident());
        GuidedMissionChecks.capture(context, "applied-check-" + source + "-" + incident.toLowerCase());
        context.runOnClient(client -> {
            final var screen = (LearningScreen) client.gui.screen();
            final var readings = screen.state();
            screen.answer(incident.equals("DUST")
                    ? readings.firstValue() > readings.secondValue() ? 0 : readings.firstValue() < readings.secondValue() ? 1 : 2
                    : supported(incident));
        });
        context.waitFor(client -> LearningClient.state(source) != null && LearningClient.state(source).solved());
        context.setScreen(() -> null);
    }
}
