package cc.nimbusnova.mc.client.hub;

import cc.nimbusnova.mc.hub.HubActionPayload;
import cc.nimbusnova.mc.hub.HubStatePayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

public final class HubClient {
    private static @Nullable JsonObject state;

    private HubClient() {
        /* w */
    }

    public static @Nullable JsonObject state() {
        return state;
    }

    public static void open() {
        if (ClientPlayNetworking.canSend(HubActionPayload.TYPE))
            ClientPlayNetworking.send(new HubActionPayload("status", ""));
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> state = null);
        ClientPlayNetworking.registerGlobalReceiver(HubStatePayload.TYPE, (packet, context) -> context.client().execute(() -> {
            state = JsonParser.parseString(packet.json()).getAsJsonObject();
            if (context.client().gui.screen() instanceof HubScreen screen)
                screen.update(state);
            else if (packet.open())
                context.client().setScreenAndShow(new HubScreen(state));
        }));
    }
}
