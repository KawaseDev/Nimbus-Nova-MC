package cc.nimbusnova.mc.client.scenario;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.scenario.ScenarioActionPayload;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import java.awt.Color;
import java.net.URI;

@NullMarked
public final class ScenarioScreen extends NovaScreen {
    private JsonObject state;
    private int left, top, panelWidth, panelHeight;
    private boolean pending;
    public ScenarioScreen(final JsonObject state) {
        super(Component.literal("Outpost mission"));
        this.state = state;
    }
    public JsonObject state() {
        return state;
    }
    public void update(final JsonObject state) {
        this.state = state;
        pending = false;
        rebuildWidgets();
    }
    public void sendTaskAction(final int station, final int value) {
        send("task", station, value);
    }
    private void send(final String action, final int station, final int value) {
        if (pending) return;
        pending = true;
        ClientPlayNetworking.send(new ScenarioActionPayload(action, station, value, state.get("revision").getAsInt()));
        rebuildWidgets();
    }
    @Override
    protected void init() {
        panelWidth = Math.min(560, width - 16); panelHeight = Math.min(320, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        addRenderableWidget(new MissionButton(left + panelWidth - 70, top + panelHeight - 29, 56, 20, "Close", this::onClose));
        addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 29, 90, 20, "NASA source", () -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(state.get("source").getAsString()))));
        final int station = state.get("station").getAsInt();
        final String phase = state.get("phase").getAsString(), id = state.get("id").getAsString();
        if (station < 0 || station == 3) {
            if (phase.equals("READY")) {
                final int buttonWidth = (panelWidth - 38) / 3;
                for (final MoonMission.Role role : MoonMission.Role.values()) {
                    final int index = role.ordinal();
                    addRenderableWidget(new MissionButton(left + 14 + index % 3 * (buttonWidth + 5), top + 86 + index / 3 * 24, buttonWidth, 21,
                            role.name().replace('_', ' '), () -> send("role", -1, index)));
                }
            }
            if (state.get("operator").getAsBoolean()) {
                final String action = phase.equals("READY") ? "start" : phase.equals("PAUSED") ? "resume" : phase.equals("ACTIVE") ? "pause" : "reset";
                addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 59, 110, 21, action + " mission", () -> send(action, -1, 0)));
            }
            return;
        }
        if (phase.equals("PAUSED") || phase.equals("SUCCESS") || phase.equals("PARTIAL") || phase.equals("FAILED") || phase.equals("ABORTED")) return;
        if (id.equals("shelter") && station == 0) {
            final int buttonWidth = (panelWidth - 38) / 3;
            for (int bearing = 0; bearing < 3; bearing++) {
                final int index = bearing;
                addRenderableWidget(new MissionButton(left + 14 + bearing * (buttonWidth + 5), top + 112, buttonWidth, 25,
                        new String[] { "Scan ridge", "Scan berm", "Scan roof" }[bearing], () -> sendTaskAction(0, index)));
            }
        } else if (id.equals("long_night") && station == 0) {
            final int buttonWidth = (panelWidth - 33) / 2;
            final String[] labels = { "Heat", "Life support", "Laboratory", "Beacon" };
            for (int load = 0; load < 4; load++) {
                final int index = load;
                addRenderableWidget(new MissionButton(left + 14 + load % 2 * (buttonWidth + 5), top + 86 + load / 2 * 29, buttonWidth, 25,
                        labels[load] + ((state.get("loads").getAsInt() & 1 << load) != 0 ? " ON" : " OFF"), () -> sendTaskAction(0, index)));
            }
        } else if (!id.equals("greenhouse") && station == 2) {
            addRenderableWidget(new MissionButton(left + 14, top + 115, (panelWidth - 33) / 2, 27, "Short pulse .", () -> sendTaskAction(2, 0)));
            addRenderableWidget(new MissionButton(left + 19 + (panelWidth - 33) / 2, top + 115, (panelWidth - 33) / 2, 27, "Long pulse ---", () -> sendTaskAction(2, 1)));
        } else if (id.equals("greenhouse") && station == 1) {
            final int buttonWidth = (panelWidth - 38) / 3;
            for (int bed = 0; bed < 3; bed++) {
                final int index = bed;
                addRenderableWidget(new MissionButton(left + 14 + bed * (buttonWidth + 5), top + 123, buttonWidth, 25,
                        "Bed " + (bed + 1) + ((state.get("valves").getAsInt() & 1 << bed) != 0 ? " CLOSE" : " WATER"), () -> sendTaskAction(1, index)));
            }
        } else if (id.equals("greenhouse") && station == 2) {
            final int buttonWidth = (panelWidth - 38) / 3;
            for (int bed = 0; bed < 3; bed++) {
                final int index = bed;
                addRenderableWidget(new MissionButton(left + 14 + bed * (buttonWidth + 5), top + 123, buttonWidth, 25,
                        "Sample bed " + (bed + 1), () -> sendTaskAction(2, index)));
            }
        }
    }
    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        final Color accent = new Color(112, 226, 232), muted = new Color(168, 187, 197), text = new Color(235, 244, 247);
        NovaRenderer.rect(graphics, left, top, panelWidth, panelHeight, 9, new Color(10, 23, 31, 245));
        NovaRenderer.text(graphics, state.get("title").getAsString(), left + 14, top + 12, 14, text);
        NovaRenderer.text(graphics, state.get("phase").getAsString() + " / " + state.get("role").getAsString().replace('_', ' '), left + 14, top + 33, 8, accent);
        final int station = state.get("station").getAsInt();
        final String id = state.get("id").getAsString();
        if (station < 0 || station == 3) {
            NovaRenderer.wrap(graphics, state.get("objective").getAsString(), left + 14, top + 53, panelWidth - 28, 9, 2, text);
        } else if (id.equals("greenhouse") && (station == 1 || station == 2)) {
            final float column = (panelWidth - 28) / 3F;
            for (int bed = 0; bed < 3; bed++) {
                final float x = left + 14 + column * bed;
                final int moisture = state.getAsJsonArray("moisture").get(bed).getAsInt(), rootAir = state.getAsJsonArray("rootAir").get(bed).getAsInt();
                NovaRenderer.text(graphics, "Bed " + (bed + 1) + " / root air " + rootAir, x, top + 57, 8, text);
                NovaRenderer.rect(graphics, x, top + 78, column - 7, 16, 4, new Color(37, 55, 65));
                NovaRenderer.rect(graphics, x, top + 78, (column - 7) * moisture / 100F, 16, 4, moisture > 80 ? new Color(246, 167, 95) : accent);
                NovaRenderer.text(graphics, "Moisture " + moisture + " / target40-70", x, top + 100, 7, muted);
            }
        } else if (!id.equals("greenhouse") && station == 2) {
            NovaRenderer.text(graphics, "SOS: . . .  --- --- ---  . . .", left + 14, top + 58, 12, accent);
            final float cell = (panelWidth - 28) / 9F;
            for (int pulse = 0; pulse < 9; pulse++)
                NovaRenderer.rect(graphics, left + 14 + pulse * cell, top + 85, cell - 4, 15, 4,
                        pulse < state.get("signal").getAsInt() ? accent : new Color(37, 55, 65));
        } else if (id.equals("long_night") && station == 0) {
            NovaRenderer.text(graphics, "Battery " + state.get("battery").getAsInt() + " / heat " + state.get("temperature").getAsInt()
                    + " / oxygen " + state.get("oxygen").getAsInt(), left + 14, top + 57, 10, accent);
        } else if (id.equals("shelter") && station == 0) {
            NovaRenderer.text(graphics, "Exposed ridge    Berm    Covered roof", left + 14, top + 57, 10, text);
            for (int bearing = 0; bearing < 3; bearing++)
                NovaRenderer.rect(graphics, left + 16 + bearing * (panelWidth - 28) / 3F, top + 82, 55 - bearing * 17, 15, 3,
                        (state.get("scans").getAsInt() & 1 << bearing) != 0 ? accent : muted);
        } else
            NovaRenderer.wrap(graphics, state.get("objective").getAsString(), left + 14, top + 60, panelWidth - 28, 10, 5, text);
        NovaRenderer.wrap(graphics, pending ? "Waiting for the server..." : state.get("feedback").getAsString(), left + 14, top + panelHeight - 75, panelWidth - 28, 8, 2, muted);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
