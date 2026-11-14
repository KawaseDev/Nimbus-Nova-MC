package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.mission.DustStorm;
import cc.nimbusnova.mc.mission.MissionDebrief;
import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;

public final class MissionConsoleScreen extends Screen implements MissionPalette {
    private int tab;
    private int panelX, panelY, panelWidth, panelHeight;
    private MissionButton start, pause, reset, oxygen, research, forecast, powerRole, lifeRole, weatherRole;

    static final int DEBRIEF_TAB = 3;

    public MissionConsoleScreen() {
        this(0);
    }

    public MissionConsoleScreen(final int tab) {
        super(Component.literal("Nimbus Nova mission console"));
        this.tab = tab;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, width - 16);
        panelHeight = Math.min(330, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        final String[] names = { "Overview", "Systems", "Briefing", "Debrief" };
        final int tabWidth = (panelWidth - 42) / 4;
        for (int index = 0; index < names.length; index++) {
            final int selectedTab = index;
            final MissionButton button = addRenderableWidget(new MissionButton(panelX + 12 + index * (tabWidth + 6),
                    panelY + 43, tabWidth, 22, names[index], () -> {
                        tab = selectedTab;
                        rebuildWidgets();
                    }));
            button.selected = index == tab;
        }

        addRenderableWidget(new MissionButton(panelX + panelWidth - 30, panelY + 10, 18, 18, "X", this::onClose));
        final int controlWidth = (panelWidth - 36) / 3, footerY = panelY + panelHeight - 32;
        start = addRenderableWidget(new MissionButton(panelX + 12, footerY, controlWidth, 22, "Start mission", () -> command("start")));
        pause = addRenderableWidget(new MissionButton(panelX + 18 + controlWidth, footerY, controlWidth, 22, "Pause", () -> {
            if (NimbusNovaClient.snapshot != null)
                command(NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.PAUSED ? "resume" : "pause");
        }));
        reset = addRenderableWidget(new MissionButton(panelX + 24 + controlWidth * 2, footerY, controlWidth, 22, "Reset", () -> command("reset")));

        final int systemWidth = (panelWidth - 36) / 3;
        powerRole = addRenderableWidget(new MissionButton(panelX + 12, panelY + 94, systemWidth, 22, "Power engineer", () -> command("role power")));
        lifeRole = addRenderableWidget(new MissionButton(panelX + 18 + systemWidth, panelY + 94, systemWidth, 22, "Life support", () -> command("role life_support")));
        weatherRole = addRenderableWidget(new MissionButton(panelX + 24 + systemWidth * 2, panelY + 94, systemWidth, 22, "Weather officer", () -> command("role weather")));
        research = addRenderableWidget(new MissionButton(panelX + 12, panelY + 144, systemWidth, 24, "Research", () -> {
            if (NimbusNovaClient.snapshot != null)
                command("equipment research " + !NimbusNovaClient.snapshot.state().researchOnline());
        }));
        oxygen = addRenderableWidget(new MissionButton(panelX + 18 + systemWidth, panelY + 144, systemWidth, 24, "Oxygen", () -> {
            if (NimbusNovaClient.snapshot != null)
                command("equipment oxygen " + !NimbusNovaClient.snapshot.state().oxygenOnline());
        }));
        forecast = addRenderableWidget(new MissionButton(panelX + 24 + systemWidth * 2, panelY + 144, systemWidth, 24, "Send forecast", () -> command("forecast")));
        updateControls();
    }

    private void command(final String command) {
        if (minecraft.getConnection() != null)
            minecraft.getConnection().sendCommand("mission " + command);
    }

    private void updateControls() {
        final MissionSnapshotPayload packet = NimbusNovaClient.snapshot;
        final boolean connected = packet != null;
        final MissionState.Phase phase = connected ? packet.state().phase() : MissionState.Phase.READY;
        final boolean operator = connected && packet.operator();
        start.active = operator && phase == MissionState.Phase.READY;
        pause.active = operator && (phase == MissionState.Phase.ACTIVE || phase == MissionState.Phase.PAUSED);
        pause.setMessage(Component.literal(phase == MissionState.Phase.PAUSED ? "Resume" : "Pause"));
        reset.active = operator && phase != MissionState.Phase.ACTIVE;
        powerRole.visible = lifeRole.visible = weatherRole.visible = oxygen.visible = research.visible = forecast.visible = tab == 1 && connected;
        powerRole.active = lifeRole.active = weatherRole.active = connected && phase == MissionState.Phase.READY;
        powerRole.selected = connected && packet.role() == CrewRole.POWER;
        lifeRole.selected = connected && packet.role() == CrewRole.LIFE_SUPPORT;
        weatherRole.selected = connected && packet.role() == CrewRole.WEATHER;
        forecast.active = connected && phase == MissionState.Phase.ACTIVE && MissionUi.seesWeather(packet);
        research.active = connected && phase == MissionState.Phase.ACTIVE && (operator || packet.role() == CrewRole.POWER);
        oxygen.active = connected && phase == MissionState.Phase.ACTIVE && (operator || packet.role() == CrewRole.LIFE_SUPPORT);

        if (!connected) return;
        research.setMessage(Component.literal("Research: " + (packet.state().researchOnline() ? "ON" : "OFF")));
        oxygen.setMessage(Component.literal("Oxygen: " + (packet.state().oxygenOnline() ? "ON" : "OFF")));
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        updateControls();
        graphics.fill(0, 0, width, height, 0xB010151C);
        MissionUi.panel(graphics, panelX, panelY, panelWidth, panelHeight);
        graphics.fill(panelX, panelY, panelX + 3, panelY + 35, ACCENT.getRGB());
        MissionUi.text(graphics, "NIMBUS NOVA", panelX + 14, panelY + 12, TEXT);
        MissionUi.text(graphics, "Prototype / authored game units", panelX + 14, panelY + 26, MUTED);

        final MissionSnapshotPayload packet = NimbusNovaClient.snapshot;
        final int contentX = panelX + 14, contentY = panelY + 78, contentWidth = panelWidth - 28;
        if (packet == null) {
            MissionUi.wrap(graphics, "Waiting for the mission server. Join a server with Nimbus Nova installed.",
                    contentX, contentY, contentWidth, 4, TEXT);
        } else {
            final MissionState.Snapshot state = packet.state();
            switch (tab) {
                case 0 -> {
                    MissionUi.text(graphics, "The darkening sky", contentX, contentY, TEXT);
                    MissionUi.rightText(graphics, MissionUi.time(MissionState.DURATION_SECONDS - state.elapsedSeconds()) + " / " + state.phase(),
                            panelX + panelWidth - 14, contentY, ACCENT);
                    final int meterWidth = (contentWidth - 16) / 2;
                    MissionUi.text(graphics, "Battery  " + Math.round(state.battery()) + "%", contentX, contentY + 23, ACCENT);
                    MissionUi.bar(graphics, contentX, contentY + 37, meterWidth, state.battery(), state.battery() <= 25 ? DANGER : ACCENT);
                    MissionUi.text(graphics, "Oxygen  " + Math.round(state.oxygen()) + "%", contentX + meterWidth + 16, contentY + 23, OXYGEN);
                    MissionUi.bar(graphics, contentX + meterWidth + 16, contentY + 37, meterWidth, state.oxygen(), state.oxygen() <= 25 ? DANGER : OXYGEN);
                    MissionUi.text(graphics, "Science " + Math.min(state.researchSeconds(), MissionState.RESEARCH_TARGET_SECONDS)
                            + "/90s  |  " + MissionUi.rate(state.generation() - state.demand()) + " energy/s"
                            + (MissionUi.seesWeather(packet) ? "  |  Sun " + MissionUi.sunPercent(state) + "%" : ""), contentX, contentY + 52, MUTED);
                    MissionUi.bar(graphics, contentX, contentY + 66, contentWidth,
                            state.researchSeconds() * 100.0 / MissionState.RESEARCH_TARGET_SECONDS, SUCCESS);
                    MissionUi.wrap(graphics, state.lastEvent(), contentX, contentY + 81, contentWidth,
                            Math.max(1, (panelHeight - 195) / 11), state.phase() == MissionState.Phase.FAILED ? DANGER : TEXT);

                    if (panelHeight >= 290) {
                        MissionUi.text(graphics, packet.role().label + "  /  " + packet.crew() + " crew online", contentX, contentY + 126, ACCENT);
                        MissionUi.text(graphics, state.decisions() + " equipment decisions / prototype game units", contentX, contentY + 143, MUTED);
                        MissionUi.text(graphics, "Survive 4 minutes and complete 90 seconds of research.", contentX, contentY + 164, TEXT);
                    }
                }
                case 1 -> {
                    MissionUi.text(graphics, "Your role: " + packet.role().label, contentX, contentY, ACCENT);
                    MissionUi.rightText(graphics, "Oxygen canisters " + state.canistersLeft() + "/" + MissionState.OXYGEN_CANISTERS,
                            panelX + panelWidth - 14, contentY, OXYGEN);
                    MissionUi.text(graphics, "Habitat equipment", contentX, contentY + 49, TEXT);
                    MissionUi.wrap(graphics, "Research uses 0.65 energy/s. Oxygen uses 0.45 energy/s. The habitat always uses 0.15. "
                                    + "Solar panels give up to 0.95 and the backup generator always gives 0.15.", contentX,
                            contentY + 100, contentWidth, Math.max(1, (panelHeight - 210) / 11), MUTED);

                    if (panelHeight >= 290) {
                        MissionUi.wrap(graphics, "Power engineers control research. Life-support engineers control oxygen. Only the weather officer "
                                        + "can see the sunlight and its 30-second forecast, so tell the crew what is coming. "
                                        + "Mission Control can do everything for solo testing. Choose roles before starting.",
                                contentX, contentY + 134, contentWidth, 4, TEXT);
                    }
                }
                case DEBRIEF_TAB -> {
                    if (!state.phase().ended()) {
                        MissionUi.wrap(graphics, "The debrief opens when the mission ends. Survive until 04:00 and finish 90 seconds of research.",
                                contentX, contentY, contentWidth, 3, MUTED);
                    } else {
                        final Color outcome = state.phase() == MissionState.Phase.SUCCESS ? SUCCESS
                                : state.phase() == MissionState.Phase.PARTIAL ? ACCENT : DANGER;
                        MissionUi.text(graphics, state.objective(), contentX, contentY, outcome);
                        int line = contentY + 18;
                        for (final String fact : MissionDebrief.facts(state)) {
                            MissionUi.text(graphics, fact, contentX, line, TEXT);
                            line += 13;
                        }

                        for (final String consequence : MissionDebrief.consequences(state)) {
                            MissionUi.wrap(graphics, consequence, contentX, line, contentWidth, 2, ACCENT);
                            line += 24;
                        }

                        MissionUi.wrap(graphics, MissionDebrief.realStory(state), contentX, line + 2, contentWidth, 3, MUTED);
                        if (panelHeight >= 290)
                            MissionUi.wrap(graphics, MissionDebrief.REFLECTION, contentX, line + 40, contentWidth, 2, TEXT);
                    }
                }
                default -> {
                    MissionUi.text(graphics, "Crew briefing", contentX, contentY, ACCENT);
                    final DustStorm storm = state.dustStorm();
                    MissionUi.wrap(graphics, "A dust storm starts dimming the sun at 01:15. By 02:15 only " + storm.peakPercent()
                                    + "% of the sunlight is left, and the dust starts to thin at 03:15. Keep battery and oxygen above zero "
                                    + "until 04:00; finish 90 seconds of research for full success. This storm is real: " + storm.summary(),
                            contentX, contentY + 18, contentWidth, panelHeight >= 290 ? 8 : 6, TEXT);

                    if (panelHeight >= 290) {
                        MissionUi.wrap(graphics, "Real data: the sunlight drop and dust opacity (tau: how much dust blocks the sun). "
                                        + "Game assumptions: battery size, energy use, about 10 sols (Mars days) squeezed into 1 minute, "
                                        + "the backup generator, and the recovery. Each reset may bring a different real storm.",
                                contentX, contentY + 116, contentWidth, 5, MUTED);
                    }
                }
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
