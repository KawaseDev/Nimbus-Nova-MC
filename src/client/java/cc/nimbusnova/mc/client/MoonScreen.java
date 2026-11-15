package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.learning.LearningClient;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.moon.MoonActionPayload;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.moon.MoonPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import java.awt.Color;
import java.net.URI;

@NullMarked
public final class MoonScreen extends NovaScreen {
    private MoonPayload state;
    private int lessonPage;
    private int left, top, panelWidth, panelHeight;

    public MoonScreen(MoonPayload state) { super(Component.literal("Earth to Moon")); this.state = state; }
    public void update(MoonPayload state) {
        if (!this.state.incident().equals(state.incident())) lessonPage = 0;
        this.state = state; rebuildWidgets();
    }
    private void send(String action) { ClientPlayNetworking.send(new MoonActionPayload(action)); }
    private static String name(Enum<?> value) { return value.name().replace('_', ' '); }

    @Override protected void init() {
        if (NimbusNovaClient.moon != null)
            state = NimbusNovaClient.moon;
        panelWidth = Math.min(620, width - 16); panelHeight = Math.min(360, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        int footer = top + panelHeight - 32;
        addRenderableWidget(new MissionButton(left + panelWidth - 72, footer, 58, 20, "Close", this::onClose));
        if (!state.incident().isEmpty()) addRenderableWidget(new MissionButton(left + 14, footer, 110, 20, "NASA source",
                () -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(MoonMission.Incident.valueOf(state.incident()).source))));
        if (!state.incident().isEmpty()) {
            MissionButton previous = addRenderableWidget(new MissionButton(left + 130, footer, 85, 20, "Previous", () -> { lessonPage = 0; rebuildWidgets(); }));
            MissionButton next = addRenderableWidget(new MissionButton(left + 220, footer, 65, 20, "Next", () -> { lessonPage = 1; rebuildWidgets(); }));
            previous.active = lessonPage > 0; next.active = lessonPage == 0;
            addRenderableWidget(new MissionButton(left + 286, footer, 52, 20, "Check", () -> LearningClient.open("moon")));
        }
        if (state.stage().isEmpty() || state.stage().equals("COMPLETE") || state.paused()) return;
        if (state.role().isEmpty()) {
            int w = (panelWidth - 40) / 3;
            int i = 0;
            for (MoonMission.Role role : MoonMission.Role.values()) {
                int index = i++;
                MissionButton button = addRenderableWidget(new MissionButton(left + 14 + index % 3 * (w + 5), footer - 53 + index / 3 * 24,
                        w, 21, name(role), () -> send("role:" + role.name())));
                button.active = (state.claimed() & 1 << role.ordinal()) == 0;
            }
        } else if (!state.role().isEmpty()) {
            MoonMission.Role role = MoonMission.Role.valueOf(state.role());
            MoonMission.Decision decision = MoonMission.decision(MoonMission.Stage.valueOf(state.stage()));
            if (decision.role() == role && !state.decisionMade()) {
                int w = (panelWidth - 33) / 2;
                addRenderableWidget(new MissionButton(left + 14, footer - 49, w, 22, decision.safe(), () -> send("safe")));
                addRenderableWidget(new MissionButton(left + 19 + w, footer - 49, w, 22, decision.alternative(), () -> send("alternative")));
            }
            MissionButton report = addRenderableWidget(new MissionButton(left + 14, footer - 24, 150, 21, "Confirm report", () -> send("confirm")));
            report.active = (state.confirmed() & 1 << role.ordinal()) == 0 && MoonMission.required(MoonMission.Stage.valueOf(state.stage())).contains(role);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Color bg = new Color(13, 23, 32, 245), white = new Color(233, 243, 244), muted = new Color(159, 180, 191), accent = new Color(110, 220, 225);
        NovaRenderer.rect(g, left, top, panelWidth, panelHeight, 10, bg);
        if (state.stage().isEmpty()) { NovaRenderer.text(g, "Moon mission ended", left + 14, top + 14, 15, white); super.extractRenderState(g, mouseX, mouseY, delta); return; }
        MoonMission.Stage stage = MoonMission.Stage.valueOf(state.stage());
        NovaRenderer.text(g, "Earth to Moon / " + name(stage), left + 14, top + 14, 15, white);
        String telemetry = switch (state.role()) {
            case "POWER" -> "Battery: " + state.battery();
            case "LIFE_SUPPORT" -> "Oxygen: " + state.oxygen();
            case "SUPPLIES" -> "Water: " + state.water();
            case "MAINTENANCE" -> "Spare parts: " + state.parts();
            case "PILOT" -> "Fuel: " + state.fuel();
            default -> "Select a role to see your readings.";
        };
        NovaRenderer.text(g, telemetry, left + 14, top + 38, 10, accent);
        int y = top + 60;
        if (!state.incident().isEmpty()) {
            MoonMission.Incident incident = MoonMission.Incident.valueOf(state.incident());
            NovaRenderer.text(g, incident.title, left + 14, y, 12, accent); y += 20;
            if (lessonPage == 0) {
                NovaRenderer.wrap(g, incident.observation + " " + incident.explanation, left + 14, y, panelWidth - 28, 10, 4, white); y += 50;
                NovaRenderer.wrap(g, incident.hazard, left + 14, y, panelWidth - 28, 10, 3, muted);
            } else {
                NovaRenderer.wrap(g, incident.question, left + 14, y, panelWidth - 28, 10, 3, accent); y += 45;
                NovaRenderer.wrap(g, incident.evidence, left + 14, y, panelWidth - 28, 9, 3, muted); y += 38;
                NovaRenderer.wrap(g, "Mission Control confirms understanding and resumes the simulation.", left + 14, y, panelWidth - 28, 9, 2, white);
            }
        } else if (stage == MoonMission.Stage.COMPLETE) {
            NovaRenderer.wrap(g, state.feedback(), left + 14, y, panelWidth - 28, 11, 3, white);
        } else {
            MoonMission.Decision decision = MoonMission.decision(stage);
            NovaRenderer.wrap(g, decision.prompt(), left + 14, y, panelWidth - 28, 12, 3, white); y += 45;
            NovaRenderer.wrap(g, state.feedback(), left + 14, y, panelWidth - 28, 10, 3, muted); y += 42;
            String role = state.role().isEmpty() ? "Choose a role below." : "Your role: " + state.role().replace('_', ' ');
            NovaRenderer.text(g, role + " / Decision: " + name(decision.role()), left + 14, y, 10, accent); y += 20;
            NovaRenderer.wrap(g, state.paused() ? "Mission paused by Mission Control." : "Required reports: " + MoonMission.required(stage), left + 14, y, panelWidth - 28, 9, 2, muted);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    @Override public boolean isPauseScreen() { return false; }
}
