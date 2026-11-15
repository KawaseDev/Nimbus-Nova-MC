package cc.nimbusnova.mc.client;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

final class FullscreenChecks {
    private FullscreenChecks() {
        /* w */
    }

    static void capture(final ClientGameTestContext context, final String label) {
        final boolean fullscreen = context.computeOnClient(client -> client.options.fullscreen().get());
        if (!fullscreen) {
            context.getInput().pressKey(options -> options.keyFullscreen);
            context.waitFor(client -> client.options.fullscreen().get());
            context.waitTicks(20);
        }
        GuidedMissionChecks.capture(context, "fullscreen-" + label);
        if (!fullscreen) {
            context.getInput().pressKey(options -> options.keyFullscreen);
            context.waitFor(client -> !client.options.fullscreen().get());
            context.waitTicks(10);
        }
    }
}
