package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.NimbusNovaMod;
import cc.nimbusnova.mc.bridge.ViewFramePayload;
import cc.nimbusnova.mc.bridge.ViewRequestPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class StudentView {
    private static final AtomicBoolean capturing = new AtomicBoolean();

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ViewRequestPayload.TYPE, (request, context) -> context.client().execute(() -> {
            if (context.client().player != context.player() || !capturing.compareAndSet(false, true)) return;
            context.player().sendOverlayMessage(Component.literal("Mission Control is viewing your game"));
            try {
                Screenshot.takeScreenshot(context.client().gameRenderer.mainRenderTarget(), image ->
                        CompletableFuture.runAsync(() -> {
                            try (image) {
                                int width = Math.min(480, image.getWidth());
                                int height = Math.max(1, image.getHeight() * width / image.getWidth());
                                try (NativeImage scaled = new NativeImage(width, height, false)) {
                                    image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), scaled);
                                    BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                                    output.setRGB(0, 0, width, height, scaled.getPixels(), 0, width);
                                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                    if (!ImageIO.write(output, "jpg", bytes) || bytes.size() > ViewFramePayload.MAX_BYTES) return;
                                    byte[] frame = bytes.toByteArray();
                                    context.client().execute(() -> {
                                        if (context.client().player == context.player() && ClientPlayNetworking.canSend(ViewFramePayload.TYPE))
                                            ClientPlayNetworking.send(new ViewFramePayload(request.id(), frame));
                                    });
                                }
                            } catch (Exception error) {
                                NimbusNovaMod.LOGGER.warn("Could not capture student view", error);
                            } finally { capturing.set(false); }
                        }));
            } catch (Exception error) {
                capturing.set(false);
                NimbusNovaMod.LOGGER.warn("Could not request student view", error);
            }
        }));
    }

    private StudentView() { }
}
