package cc.nimbusnova.mc.client.lesson;

import cc.nimbusnova.mc.lesson.LessonPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ScienceStationClient {
    private ScienceStationClient() {
        /* w */
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LessonPayload.TYPE, (packet, context) -> context.client().execute(() -> {
            if (context.client().gui.screen() instanceof ScienceStationScreen screen && screen.position().equals(packet.position()))
                screen.update(packet);
            else
                context.client().setScreenAndShow(new ScienceStationScreen(packet));
        }));
    }
}
