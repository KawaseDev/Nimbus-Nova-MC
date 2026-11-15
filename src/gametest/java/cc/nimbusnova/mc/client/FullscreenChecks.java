package cc.nimbusnova.mc.client;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;

final class FullscreenChecks {
    private FullscreenChecks() {
        /* w */
    }

    static void capture(final ClientGameTestContext context, final String label) {
        final boolean fullscreen = context.computeOnClient(FullscreenChecks::isFullscreen);
        if (!fullscreen) {
            context.getInput().pressKey(options -> options.keyFullscreen);
            context.waitFor(FullscreenChecks::isFullscreen);
            context.waitTicks(20);
        }
        GuidedMissionChecks.capture(context, "fullscreen-" + label);
        if (!fullscreen) {
            context.getInput().pressKey(options -> options.keyFullscreen);
            context.waitFor(client -> !isFullscreen(client));
            context.waitTicks(10);
        }
    }

    private static boolean isFullscreen(final Minecraft client) {
        return (SDLVideo.SDL_GetWindowFlags(client.getWindow().handle()) & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0;
    }
}
