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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@NullMarked
public final class HubScreen extends NovaScreen implements ShipPalette {
    private JsonObject state;
    private String chosen;
    private String error;
    private int left, top, panelWidth, panelHeight, listWidth, detailX, detailWidth;
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
        listWidth = Math.clamp(panelWidth * 42 / 100, 140, 230);
        detailX = left + 14 + listWidth + 6;
        detailWidth = left + panelWidth - 14 - detailX;

        final int footer = top + panelHeight - 27, missions = state.getAsJsonArray("missions").size();
        final int rowHeight = Math.clamp((footer - top - 42) / Math.max(1, missions) - 2, 16, 26);
        int index = 0;
        for (final var item : state.getAsJsonArray("missions")) {
            final JsonObject mission = item.getAsJsonObject();
            final String id = mission.get("id").getAsString();
            final MissionButton button = addRenderableWidget(new MissionButton(left + 14, top + 37 + index * (rowHeight + 2),
                    listWidth, rowHeight, mission.get("title").getAsString(), () -> {
                        chosen = id;
                        rebuildWidgets();
                    }).withIcon(icon(id)).alignLeft());
            button.selected = chosen.equals(id);
            button.active = !pending;
            index++;
        }

        final String current = state.get("selected").getAsString(), phase = state.get("phase").getAsString();
        addRenderableWidget(new MissionButton(left + panelWidth - 70, footer, 56, 20, "Close", this::onClose));
        addRenderableWidget(new MissionButton(left + 14, footer, 72, 20, "Join crew", () -> send("join"))
                .withTone(NovaRenderer.Tone.PRIMARY)).active = !pending && !current.isEmpty();
        addRenderableWidget(new MissionButton(left + 90, footer, 83, 20, "Crew tablet", () -> send("continue"))).active = !pending && !current.isEmpty();
        if (phase.equals("READY") && state.has("ready") && state.get("ready").getAsBoolean()) {
            final boolean requested = state.has("startRequested") && state.get("startRequested").getAsBoolean();
            addRenderableWidget(new MissionButton(left + 177, footer, Math.min(135, panelWidth - 251), 20,
                    requested ? "Request sent" : "Ask teacher to start", () -> send("request_start"))).active = !pending && !requested;
        }
        if (!state.get("operator").getAsBoolean()) return;

        final int controls = footer - 31, controlWidth = (detailWidth - 20) / 3;
        addRenderableWidget(new MissionButton(detailX + 6, controls, controlWidth, 20, "Select", () -> send("select"))
                .withTone(NovaRenderer.Tone.PRIMARY)).active = !pending && !chosen.isEmpty() && !chosen.equals(current);
        final boolean lesson = phase.equals("PAUSED") && state.has("incident") && !state.get("incident").getAsString().isEmpty()
                && !(state.has("crewCheckSolved") && state.get("crewCheckSolved").getAsBoolean());
        final String control = lesson ? "continue" : phase.equals("READY") ? "start" : phase.equals("PAUSED") ? "resume" : phase.equals("ACTIVE") ? "pause" : "reset";
        final String label = switch (control) { case "start" -> "Start"; case "resume" -> "Resume"; case "pause" -> "Pause"; case "continue" -> "Crew check"; default -> "Reset"; };
        addRenderableWidget(new MissionButton(detailX + 10 + controlWidth, controls, controlWidth, 20, label, () -> send(control))
                .withTone(control.equals("start") || control.equals("resume") ? NovaRenderer.Tone.SUCCESS : NovaRenderer.Tone.NORMAL))
                .active = !pending && !current.isEmpty() && (!control.equals("start") || state.has("ready") && state.get("ready").getAsBoolean());
        addRenderableWidget(new MissionButton(detailX + 14 + controlWidth * 2, controls, controlWidth, 20, "End", () -> send("end"))
                .withTone(NovaRenderer.Tone.DANGER)).active = !pending && !current.isEmpty();
    }

    private static Item icon(final String id) {
        return switch (id) {
            case "mars" -> Items.RED_SAND;
            case "moon" -> Items.CALCITE;
            case "ship" -> Items.FIREWORK_ROCKET;
            case "shelter" -> Items.SHIELD;
            case "long_night" -> Items.REDSTONE;
            case "greenhouse" -> Items.WHEAT_SEEDS;
            default -> Items.COMPASS;
        };
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        NovaRenderer.window(graphics, left, top, panelWidth, panelHeight, BACKGROUND);
        NovaRenderer.title(graphics, left, top, panelWidth, "Mission Control");

        final String current = state.get("selected").getAsString(), phase = state.get("phase").getAsString();
        String title = "Choose a mission", description = "Pick a mission on the left to see what your crew will do.", live = "";
        for (final var item : state.getAsJsonArray("missions")) {
            final JsonObject mission = item.getAsJsonObject();
            if (mission.get("id").getAsString().equals(current)) live = mission.get("title").getAsString();
            if (!mission.get("id").getAsString().equals(chosen)) continue;
            title = mission.get("title").getAsString();
            description = mission.get("description").getAsString();
        }

        final String status = current.isEmpty() ? "NO MISSION LIVE" : "LIVE: " + live.toUpperCase(Locale.ROOT) + " / " + phase;
        if (NovaRenderer.chipWidth(status) < panelWidth - 150)
            NovaRenderer.chip(graphics, status, left + panelWidth - 12 - NovaRenderer.chipWidth(status), top + 11,
                    current.isEmpty() ? MUTED : phase.equals("ACTIVE") ? SUCCESS : ACCENT);

        final int footer = top + panelHeight - 27, detailTop = top + 37, detailHeight = footer - 6 - detailTop;
        NovaRenderer.slot(graphics, detailX, detailTop, detailWidth, detailHeight, SURFACE, false);
        NovaRenderer.slot(graphics, detailX + 6, detailTop + 6, 20, 20, BACKGROUND, false);
        graphics.item(new ItemStack(icon(chosen)), detailX + 8, detailTop + 8);
        NovaRenderer.text(graphics, title, detailX + 31, detailTop + 7, 10, TEXT);

        final String[] facts = facts(chosen);
        NovaRenderer.text(graphics, facts[0], detailX + 31, detailTop + 18, 8, ACCENT);

        final int textWidth = detailWidth - 14, controls = state.get("operator").getAsBoolean() ? 30 : 4;
        final int available = (detailHeight - 34 - controls) / 11;
        final boolean switching = !current.isEmpty() && !chosen.equals(current) && state.get("operator").getAsBoolean();
        final String body = !error.isEmpty() ? error : switching ? "Selecting this ends the live attempt. " + description : description;
        final int bodyLines = Math.min(Math.max(1, available - 3), NovaRenderer.lines(body, textWidth, 8));
        NovaRenderer.wrap(graphics, body, detailX + 7, detailTop + 33, textWidth, 8, bodyLines, error.isEmpty() ? TEXT : WARNING);
        if (available - bodyLines < 2) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }

        final int learnTop = detailTop + 40 + bodyLines * 11;
        NovaRenderer.text(graphics, "YOU WILL LEARN", detailX + 7, learnTop, 8, SUCCESS);
        NovaRenderer.wrap(graphics, facts[1], detailX + 7, learnTop + 11, textWidth, 8, available - bodyLines - 1, MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private static String[] facts(final String id) {
        return switch (id) {
            case "mars" -> new String[] { "MARS / 3 ROLES", "Why a dust storm starves solar panels, using NASA's Opportunity rover readings." };
            case "moon" -> new String[] { "MOON / 5 ROLES", "How crews fly to the Moon in steps: dock, orbit, land, sample and come home." };
            case "ship" -> new String[] { "DEEP SPACE / 5 ROLES", "How the Apollo 13 crew saved power and cleaned their air to get home." };
            case "shelter" -> new String[] { "MARS / FIELD WORK", "Why thick walls and soil protect astronauts from solar particle events." };
            case "long_night" -> new String[] { "MOON / ENERGY", "Why a two-week lunar night forces a crew to budget stored power." };
            case "greenhouse" -> new String[] { "HABITAT / FOOD", "Why space plants need balanced water, air at the roots and light." };
            default -> new String[] { "TRAINING", "Work as a crew: share readings, then decide together." };
        };
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
