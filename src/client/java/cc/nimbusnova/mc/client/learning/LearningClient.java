package cc.nimbusnova.mc.client.learning;

import cc.nimbusnova.mc.learning.LearningActionPayload;
import cc.nimbusnova.mc.learning.LearningPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class LearningClient {
    private static final Map<String, LearningPayload> STATES = new HashMap<>();

    private LearningClient() {
        /* w */
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> STATES.clear());
        ClientPlayNetworking.registerGlobalReceiver(LearningPayload.TYPE, (packet, context) -> context.client().execute(() -> {
            if (packet.incident().isEmpty()) STATES.remove(packet.source());
            else STATES.put(packet.source(), packet);
            if (context.client().gui.screen() instanceof LearningScreen screen && screen.state().source().equals(packet.source())) {
                if (packet.incident().isEmpty()) screen.onClose();
                else screen.update(packet);
            } else if (packet.open() && !packet.incident().isEmpty())
                context.client().setScreenAndShow(new LearningScreen(context.client().gui.screen(), packet));
        }));
    }

    public static @Nullable LearningPayload state(final String source) {
        return STATES.get(source);
    }

    public static boolean solved(final String source, final String incident) {
        final LearningPayload state = STATES.get(source);
        return state != null && state.incident().equals(incident) && state.solved();
    }

    public static void open(final String source) {
        if (ClientPlayNetworking.canSend(LearningActionPayload.TYPE))
            ClientPlayNetworking.send(new LearningActionPayload(source, 0, -1));
    }
}
