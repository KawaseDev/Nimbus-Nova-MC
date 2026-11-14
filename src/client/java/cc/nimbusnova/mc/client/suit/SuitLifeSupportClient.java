package cc.nimbusnova.mc.client.suit;

import cc.nimbusnova.mc.suit.SuitAirPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

public final class SuitLifeSupportClient {
    private static @Nullable SuitAirPayload state;

    private SuitLifeSupportClient() {
        /* w */
    }

    public static @Nullable SuitAirPayload state() {
        return state;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SuitAirPayload.TYPE,
                (packet, context) -> context.client().execute(() -> state = packet));
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> state = null);
    }
}
