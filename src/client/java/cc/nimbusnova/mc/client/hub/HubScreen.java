package cc.nimbusnova.mc.client.hub;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.client.ship.ShipPalette;
import cc.nimbusnova.mc.hub.HubActionPayload;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.stream.Stream;

@NullMarked
public final class HubScreen extends NovaScreen implements ShipPalette {
    private JsonObject state;
    private String chosen;
    private String error;
    private int left, top, panelWidth, panelHeight;
    private boolean pending;

    public HubScreen(final JsonObject state) {
        super(Component.literal("Nimbus Nova Mission Control"));
        this.state = state;
        chosen = state.get("selected").getAsString();
        error = state.get("error").getAsString();
    }

    public JsonObject state() {
        return state;
    }

    public void update(final JsonObject state, final boolean response) {
        final boolean controlsChanged = response && pending
                || Stream.of("missions", "selected", "phase", "operator", "incident", "crewCheckSolved", "ready", "startRequested")
                .anyMatch(key -> !Objects.equals(this.state.get(key), state.get(key)));
        this.state = state;
        if (response) {
            pending = false;
            error = state.get("error").getAsString();
        }

        if (controlsChanged)
            rebuildWidgets();
    }

    private void send(final String action) {
        if (pending) return;
        pending = true;
        error = "";
        ClientPlayNetworking.send(new HubActionPayload(action, chosen));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(590, width - 16);
        panelHeight = Math.min(310, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        final int cardWidth = (panelWidth - 33) / 2;
        int index = 0;
        for (final var item : state.getAsJsonArray("missions")) {
            final JsonObject mission = item.getAsJsonObject();
            final String id = mission.get("id").getAsString();
            final var icon = switch (id) {
                case "mars" -> Items.RED_SAND;
                case "moon" -> Items.CALCITE;
                case "ship" -> Items.FIREWORK_ROCKET;
                case "shelter" -> Items.SHIELD;
                case "long_night" -> Items.REDSTONE;
                case "greenhouse" -> Items.WHEAT_SEEDS;
                default -> Items.COMPASS;
            };
            final MissionButton button = addRenderableWidget(new MissionButton(left + 14 + index % 2 * (cardWidth + 5),
                    top + 45 + index / 2 * 28, cardWidth, 24, mission.get("title").getAsString(), () -> {
                        chosen = id;
                        rebuildWidgets();
                    }).withIcon(icon));
            button.selected = chosen.equals(id);
            button.active = !pending;
            index++;
        }

        final String current = state.get("selected").getAsString(), phase = state.get("phase").getAsString();
        final int footer = top + panelHeight - 29;
        addRenderableWidget(new MissionButton(left + panelWidth - 70, footer, 56, 20, "Close", this::onClose));
        addRenderableWidget(new MissionButton(left + 14, footer, 72, 20, "Join crew", () -> send("join"))).active = !pending && !current.isEmpty();
        addRenderableWidget(new MissionButton(left + 91, footer, 83, 20, "Crew tablet", () -> send("continue"))).active = !pending && !current.isEmpty();
        if (phase.equals("READY") && state.has("ready") && state.get("ready").getAsBoolean()) {
            final boolean requested = state.has("startRequested") && state.get("startRequested").getAsBoolean();
            addRenderableWidget(new MissionButton(left + 179, footer, 135, 20, requested ? "Request sent" : "Ask teacher to start", () -> send("request_start")))
                    .active = !pending && !requested;
        }
        if (!state.get("operator").getAsBoolean()) return;

        final int controls = footer - 25;
        addRenderableWidget(new MissionButton(left + 14, controls, 99, 20, "Select mission", () -> send("select"))).active = !pending && !chosen.isEmpty() && !chosen.equals(current);
        final boolean lesson = phase.equals("PAUSED") && state.has("incident") && !state.get("incident").getAsString().isEmpty()
                && !(state.has("crewCheckSolved") && state.get("crewCheckSolved").getAsBoolean());
        final String control = lesson ? "continue" : phase.equals("READY") ? "start" : phase.equals("PAUSED") ? "resume" : phase.equals("ACTIVE") ? "pause" : "reset";
        final String label = switch (control) { case "start" -> "Start"; case "resume" -> "Resume"; case "pause" -> "Pause"; case "continue" -> "Crew check"; default -> "Reset"; };
        addRenderableWidget(new MissionButton(left + 118, controls, 66, 20, label, () -> send(control)))
                .active = !pending && !current.isEmpty() && (!control.equals("start") || state.has("ready") && state.get("ready").getAsBoolean());
        addRenderableWidget(new MissionButton(left + 189, controls, 66, 20, "End", () -> send("end"))).active = !pending && !current.isEmpty();
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        NovaRenderer.window(graphics, left, top, panelWidth, panelHeight, BACKGROUND);
        NovaRenderer.title(graphics, left, top, panelWidth, "Nimbus Nova / Mission Control");
        final String current = state.get("selected").getAsString();
        NovaRenderer.text(graphics, current.isEmpty() ? "Choose your classroom's next mission" : "Selected: " + current.replace('_', ' ') + " / " + state.get("phase").getAsString(),
                left + 14, top + 32, 8, ACCENT);
        String description = "Selecting a different mission ends the current attempt and preserves the maps.";
        for (final var item : state.getAsJsonArray("missions")) {
            final JsonObject mission = item.getAsJsonObject();
            if (mission.get("id").getAsString().equals(chosen)) description = mission.get("description").getAsString();
        }
        if (!current.isEmpty() && !chosen.equals(current))
            description = "Switching ends the current attempt. " + description;
        NovaRenderer.slot(graphics, left + 14, top + 131, panelWidth - 28, 30, SURFACE, false);
        NovaRenderer.wrap(graphics, error.isEmpty() ? description : error, left + 20, top + 137, panelWidth - 40, 8, 2,
                error.isEmpty() ? MUTED : WARNING);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        if (ClientPlayNetworking.canSend(HubActionPayload.TYPE))
            ClientPlayNetworking.send(new HubActionPayload("close", ""));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
