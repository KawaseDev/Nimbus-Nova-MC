package cc.nimbusnova.mc.client.content.scifi;

public final class ScifiBlockClient {
    private ScifiBlockClient() {
        /* w */
    }

    public static void register() {
        // Minecraft 26.3 selects TRANSLUCENT from the model texture's force_translucent flag.
        // The asset owns this setup; the removed BlockRenderLayerMap API is unnecessary.
    }
}
