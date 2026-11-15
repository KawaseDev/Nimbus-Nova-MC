package cc.nimbusnova.mc.client.scenario;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.scenario.ScenarioActionPayload;
import cc.nimbusnova.mc.hub.HubActionPayload;
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
                final String label = switch (action) {
                    case "start" -> "Start mission";
                    case "resume" -> "Resume mission";
                    case "pause" -> "Pause mission";
                    default -> "Reset mission";
                };
                addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 59, 110, 21, label, () -> send(action, -1, 0))).active =
                        !pending && (!action.equals("start") || state.get("ready").getAsBoolean());
            } else if (phase.equals("READY") && state.get("ready").getAsBoolean())
                addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 59, 180, 21, "Ask teacher to start", () -> {
                    if (ClientPlayNetworking.canSend(HubActionPayload.TYPE)) ClientPlayNetworking.send(new HubActionPayload("request_start", ""));
                }));
            return;
        }
        if (phase.equals("PAUSED") || phase.equals("SUCCESS") || phase.equals("PARTIAL") || phase.equals("FAILED") || phase.equals("ABORTED")) return;
        if (id.equals("shelter") && station == 0) {
            if (phase.equals("READY")) {
                final int buttonWidth = (panelWidth - 38) / 3;
                for (int bearing = 0; bearing < 3; bearing++)
                    taskButton(new String[] { "Scan ridge", "Scan berm", "Scan roof" }[bearing], left + 14 + bearing * (buttonWidth + 5), top + 112, buttonWidth, 0, bearing);
            } else
                taskButton(state.get("observation").getAsBoolean() ? "Live data secured" : state.get("scanActive").getAsBoolean() ? "Stay beside sensor..." : "Begin three-second event scan", left + 14, top + 112, panelWidth - 28, 0, 3);
        } else if (!id.equals("greenhouse") && station == 1) {
            if (id.equals("long_night"))
                taskButton(state.get("labArmed").getAsBoolean() ? "Recorder armed" : "Arm laboratory recorder", left + 14, top + 85, panelWidth - 28, 1, 0);
            taskButton("Flow -", left + 14, top + 117, (panelWidth - 33) / 2, 1, id.equals("shelter") ? 0 : 1);
            taskButton("Flow +", left + 19 + (panelWidth - 33) / 2, top + 117, (panelWidth - 33) / 2, 1, id.equals("shelter") ? 1 : 2);
        } else if (id.equals("long_night") && station == 0) {
            final int buttonWidth = (panelWidth - 33) / 2;
            final String[] labels = { "Heat", "Life support", "Laboratory", "Beacon" };
            for (int load = 0; load < 4; load++)
                taskButton(labels[load] + ((state.get("loads").getAsInt() & 1 << load) != 0 ? " ON" : " OFF"), left + 14 + load % 2 * (buttonWidth + 5), top + 86 + load / 2 * 29, buttonWidth, 0, load);
        } else if (!id.equals("greenhouse") && station == 2) {
            taskButton("Short pulse .", left + 14, top + 115, (panelWidth - 33) / 2, 2, 0);
            taskButton("Long pulse ---", left + 19 + (panelWidth - 33) / 2, top + 115, (panelWidth - 33) / 2, 2, 1);
        } else if (id.equals("greenhouse") && station == 0) {
            taskButton(state.get("pumpInspected").getAsBoolean() ? "Pump inspected" : "Inspect feed pump", left + 14, top + 81, panelWidth - 28, 0, 3);
            final int buttonWidth = (panelWidth - 38) / 3;
            for (int bed = 0; bed < 3; bed++)
                taskButton("Lamp " + (bed + 1) + ((state.get("growLights").getAsInt() & 1 << bed) != 0 ? " ON" : " OFF"), left + 14 + bed * (buttonWidth + 5), top + 119, buttonWidth, 0, bed);
        } else if (id.equals("greenhouse") && station == 1) {
            final int buttonWidth = (panelWidth - 38) / 3;
            for (int bed = 0; bed < 3; bed++)
                taskButton("Bed " + (bed + 1) + ((state.get("valves").getAsInt() & 1 << bed) != 0 ? " CLOSE" : " WATER"), left + 14 + bed * (buttonWidth + 5), top + 123, buttonWidth, 1, bed);
        } else if (id.equals("greenhouse") && station == 2) {
            taskButton("Vent " + (state.get("extractionVent").getAsBoolean() ? "ON" : "OFF"), left + panelWidth - 123, top + 52, 109, 2, 3);
            final int buttonWidth = (panelWidth - 38) / 3;
            for (int bed = 0; bed < 3; bed++)
                taskButton("Sample bed " + (bed + 1), left + 14 + bed * (buttonWidth + 5), top + 123, buttonWidth, 2, bed);
        }
    }

    private void taskButton(final String label, final int x, final int y, final int width, final int station, final int value) {
        final MoonMission.Role owner = station == 0 ? state.get("id").getAsString().equals("greenhouse") && value == 3 ? MoonMission.Role.MAINTENANCE : MoonMission.Role.POWER
                : station == 1 ? state.get("id").getAsString().equals("long_night") && value == 0 ? MoonMission.Role.MAINTENANCE : MoonMission.Role.LIFE_SUPPORT
                : state.get("id").getAsString().equals("greenhouse") && value < 3 ? MoonMission.Role.SUPPLIES : MoonMission.Role.PILOT;
        addRenderableWidget(new MissionButton(x, y, width, 25, label, () -> sendTaskAction(station, value))).active = !pending
                && (state.get("operator").getAsBoolean() || owner.name().equals(state.get("role").getAsString()));
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        final Color accent = new Color(112, 226, 232), muted = new Color(168, 187, 197), text = new Color(235, 244, 247);
        NovaRenderer.rect(graphics, left, top, panelWidth, panelHeight, 9, new Color(10, 23, 31, 245));
        NovaRenderer.text(graphics, state.get("title").getAsString(), left + 14, top + 12, 14, text);
        final String role = state.get("role").getAsString().replace('_', ' ');
        NovaRenderer.text(graphics, state.get("phase").getAsString() + (role.isEmpty() ? "" : " / " + role), left + 14, top + 33, 8, accent);
        final int station = state.get("station").getAsInt();
        final String id = state.get("id").getAsString();
        if (station < 0 || station == 3) {
            NovaRenderer.wrap(graphics, state.get("roleObjective").getAsString(), left + 14, top + 53, panelWidth - 28, 9, 2, text);
        } else if (id.equals("greenhouse") && station == 1) {
            final float column = (panelWidth - 28) / 3F;
            for (int bed = 0; bed < 3; bed++) {
                final float x = left + 14 + column * bed;
                final int moisture = state.getAsJsonArray("moisture").get(bed).getAsInt(), rootAir = state.getAsJsonArray("rootAir").get(bed).getAsInt();
                NovaRenderer.text(graphics, "Bed " + (bed + 1) + " / root air " + rootAir, x, top + 57, 8, text);
                NovaRenderer.rect(graphics, x, top + 78, column - 7, 16, 4, new Color(37, 55, 65));
                NovaRenderer.rect(graphics, x, top + 78, (column - 7) * moisture / 100F, 16, 4, moisture > 80 ? new Color(246, 167, 95) : accent);
                NovaRenderer.text(graphics, "Moisture " + moisture + " / target 40-70", x, top + 100, 7, muted);
            }
        } else if (id.equals("greenhouse") && station == 2) {
            NovaRenderer.text(graphics, "Cabin humidity " + state.get("cabinHumidity").getAsInt() + " /40-70", left + 14, top + 57, 8, accent);
            final float column = (panelWidth - 28) / 3F;
            for (int bed = 0; bed < 3; bed++) {
                final float x = left + 14 + column * bed;
                NovaRenderer.text(graphics, "Bed " + (bed + 1) + ": moisture " + state.getAsJsonArray("moisture").get(bed).getAsInt(), x, top + 85, 8, text);
                NovaRenderer.text(graphics, "Root air " + state.getAsJsonArray("rootAir").get(bed).getAsInt() + " / lamp "
                        + state.getAsJsonArray("lightSeconds").get(bed).getAsInt() + "/10s", x, top + 101, 7, muted);
            }
        } else if (!id.equals("greenhouse") && station == 2) {
            NovaRenderer.text(graphics, "SOS: . . .  --- --- ---  . . .", left + 14, top + 58, 12, accent);
            final float cell = (panelWidth - 28) / 9F;
            for (int pulse = 0; pulse < 9; pulse++)
                NovaRenderer.rect(graphics, left + 14 + pulse * cell, top + 85, cell - 4, 15, 4,
                        pulse < state.get("signal").getAsInt() ? accent : new Color(37, 55, 65));
        } else if (id.equals("long_night") && station == 0) {
            NovaRenderer.text(graphics, "Battery " + Math.round(state.get("battery").getAsDouble()) + " / heat " + Math.round(state.get("temperature").getAsDouble())
                    + " / oxygen " + Math.round(state.get("oxygen").getAsDouble()), left + 14, top + 57, 10, accent);
        } else if (id.equals("shelter") && station == 0) {
            if (!state.get("phase").getAsString().equals("READY")) {
                NovaRenderer.text(graphics, "Live scan " + state.get("eventScanSeconds").getAsInt() + "/3s / dose " + state.get("dose").getAsInt() + "/100", left + 14, top + 58, 11, accent);
                NovaRenderer.wrap(graphics, "Three active seconds by this sensor during seconds 45-90; return to mass shielding after the measurement.", left + 14, top + 80, panelWidth - 28, 8, 2, text);
            } else {
            NovaRenderer.text(graphics, "Exposed ridge    Berm    Covered roof", left + 14, top + 57, 10, text);
            for (int bearing = 0; bearing < 3; bearing++)
                NovaRenderer.rect(graphics, left + 16 + bearing * (panelWidth - 28) / 3F, top + 82, 55 - bearing * 17, 15, 3,
                        (state.get("scans").getAsInt() & 1 << bearing) != 0 ? accent : muted);
            }
        } else if (!id.equals("greenhouse") && station == 1) {
            NovaRenderer.text(graphics, "Breathing flow " + state.get("lifeFlow").getAsInt() + " / crew demand 2", left + 14, top + 57, 10, accent);
            if (id.equals("long_night"))
                NovaRenderer.text(graphics, "Verified laboratory " + state.get("labSeconds").getAsInt() + "/30s", left + 14, top + 73, 8, muted);
        } else if (id.equals("greenhouse") && station == 0) {
            NovaRenderer.wrap(graphics, "Maintenance inspects the pump; power runs observation lamps for at least ten seconds per bed.", left + 14, top + 57, panelWidth - 28, 8, 2, text);
        } else
            NovaRenderer.wrap(graphics, state.get("objective").getAsString(), left + 14, top + 60, panelWidth - 28, 10, 5, text);
        if (!id.equals("greenhouse") && station == 2 && state.get("signal").getAsInt() == 9)
            NovaRenderer.text(graphics, "Earth acknowledgement " + state.get("acknowledgement").getAsInt() + "/5 active seconds", left + 14, top + 104, 8, muted);
        NovaRenderer.wrap(graphics, pending ? "Waiting for the server..." : state.get("feedback").getAsString(), left + 14, top + panelHeight - 75, panelWidth - 28, 8, 2, muted);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
