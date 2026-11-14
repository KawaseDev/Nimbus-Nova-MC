package cc.nimbusnova.mc.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@NullMarked
final class JoinSessionScreen extends Screen {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static @Nullable String pendingNickname;
    private final Screen parent;
    private @Nullable EditBox nickname;
    private @Nullable EditBox code;
    private @Nullable EditBox service;
    private @Nullable Button join;
    private String status = "";

    JoinSessionScreen(final Screen parent) {
        super(Component.literal("Join session"));
        this.parent = parent;
    }

    static @Nullable String takeNickname() {
        String result = pendingNickname;
        pendingNickname = null;
        return result;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100, top = height / 2 - 75;
        nickname = new EditBox(font, left, top, 200, 20, Component.literal("Nickname"));
        nickname.setMaxLength(32);
        addRenderableWidget(nickname);
        code = new EditBox(font, left, top + 40, 200, 20, Component.literal("Session code"));
        code.setMaxLength(8);
        addRenderableWidget(code);
        service = new EditBox(font, left, top + 80, 200, 20, Component.literal("Session server"));
        service.setMaxLength(255);
        service.setValue("127.0.0.1:3002");
        addRenderableWidget(service);
        join = Button.builder(Component.literal("Join"), button -> join())
                .bounds(left, top + 115, 200, 20).build();
        addRenderableWidget(join);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left, top + 139, 200, 20).build());
    }

    private void join() {
        if (nickname == null || code == null || service == null || join == null) return;
        String name = nickname.getValue().trim();
        String sessionCode = code.getValue().trim().toUpperCase(Locale.ROOT);
        String endpoint = service.getValue().trim();
        if (name.isEmpty() || name.length() > 32 || name.codePoints().anyMatch(Character::isISOControl) ||
                !sessionCode.matches("[A-HJ-NP-Z2-9]{8}")) {
            status = "Enter a nickname and valid session code.";
            return;
        }
        URI base;
        try {
            base = URI.create("http://" + endpoint);
            if (base.getHost() == null || base.getPort() < 1 || base.getPort() > 65535 || base.getUserInfo() != null ||
                    !base.getPath().isEmpty() || base.getQuery() != null || base.getFragment() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            status = "Enter the session server address and port.";
            return;
        }
        join.active = false;
        status = "Connecting…";
        JsonObject body = new JsonObject();
        body.addProperty("code", sessionCode);
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(base.resolve("/resolve"))
                        .timeout(Duration.ofSeconds(6)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
                HttpResponse<java.io.InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var stream = response.body()) {
                    if (response.statusCode() != 200) throw new IllegalStateException();
                    byte[] bytes = stream.readNBytes(4097);
                    if (bytes.length > 4096) throw new IllegalStateException();
                    String address = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject().get("address").getAsString();
                    if (!ServerAddress.isValidAddress(address) || !ServerAddress.parseString(address).getHost().equalsIgnoreCase(base.getHost()))
                        throw new IllegalStateException();
                    return address;
                }
            } catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }).whenComplete((address, error) -> minecraft.execute(() -> {
            if (minecraft.gui.screen() != this) return;
            if (error != null || address == null) {
                status = "Could not find the session. Check the code and server.";
                if (join != null) join.active = true;
                return;
            }
            pendingNickname = name;
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(address),
                    new ServerData("Nimbus Nova", address, ServerData.Type.OTHER), false, null);
        }));
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = width / 2 - 100, top = height / 2 - 75;
        graphics.centeredText(font, title, width / 2, top - 30, 0xFFFFFFFF);
        graphics.text(font, "Nickname", left, top - 12, 0xFFA0A0A0);
        graphics.text(font, "Session code", left, top + 28, 0xFFA0A0A0);
        graphics.text(font, "Session server", left, top + 68, 0xFFA0A0A0);
        graphics.centeredText(font, status, width / 2, top + 105, 0xFFFFA0A0);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
