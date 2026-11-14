package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionPreparation;
import cc.nimbusnova.mc.mission.MissionProgressPayload;
import cc.nimbusnova.mc.mission.MissionTaskOpenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GuidedMissionClient {
    private static MissionProgressPayload progress;

    private GuidedMissionClient() {
        /* w */
    }

    public static MissionProgressPayload progress() {
        return progress;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MissionProgressPayload.TYPE,
                (packet, context) -> context.client().execute(() -> {
                    progress = packet;
                    if (context.client().gui.screen() instanceof TaskScreen task)
                        task.syncProgress();
                }));
        ClientPlayNetworking.registerGlobalReceiver(MissionTaskOpenPayload.TYPE, (packet, context) -> context.client().execute(() -> {
            if (packet.kind() != MissionPreparation.TaskKind.POWER && packet.kind() != MissionPreparation.TaskKind.OXYGEN) return;
            if (context.client().gui.screen() instanceof TaskScreen task && task.serverState().kind() == packet.kind())
                task.acceptState(packet);
            else
                context.client().setScreenAndShow(new TaskScreen(packet));
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> progress = null);
    }
}
