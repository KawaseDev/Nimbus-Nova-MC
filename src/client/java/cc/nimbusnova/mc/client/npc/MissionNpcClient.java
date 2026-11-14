package cc.nimbusnova.mc.client.npc;

import cc.nimbusnova.mc.npc.NpcDialoguePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MissionNpcClient {
    private MissionNpcClient() {
        /* w */
    }

    public static void register() {
        MissionNpcRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(NpcDialoguePayload.TYPE,
                (payload, context) -> context.client().execute(() -> context.client().setScreenAndShow(new NpcDialogueScreen(payload))));
    }
}
