package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.mission.DustStorm;
import cc.nimbusnova.mc.mission.MissionDebrief;
import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class MissionConsoleScreen extends NovaScreen implements MissionPalette {
    private int tab;
    private int panelX, panelY, panelWidth, panelHeight;
    private MissionButton start, pause, reset, oxygen, research, forecast, powerRole, lifeRole, weatherRole;
    private MissionButton previousDebrief, nextDebrief;
    private int debriefPage, debriefLinesPerPage;
    private MissionState.Snapshot debriefState;
    private final List<DebriefLine> debriefLines = new ArrayList<>();

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
        MissionTutorial.consoleOpened = true;
        panelWidth = Math.min(560, width - 16);
        panelHeight = Math.min(330, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        debriefState = null;
        debriefLinesPerPage = Math.max(2, (panelHeight - 156) / 14);

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
        previousDebrief = addRenderableWidget(new MissionButton(panelX + 14, footerY - 28, 78, 22,
                "Previous", () -> changeDebriefPage(-1)));
        nextDebrief = addRenderableWidget(new MissionButton(panelX + panelWidth - 92, footerY - 28, 78, 22,
                "Next", () -> changeDebriefPage(1)));

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
        previousDebrief.visible = nextDebrief.visible = connected && tab == DEBRIEF_TAB && phase.ended();

        if (!connected) return;
        if (previousDebrief.visible) {
            prepareDebrief(packet.state());
            previousDebrief.active = debriefPage > 0;
            nextDebrief.active = (debriefPage + 1) * debriefLinesPerPage < debriefLines.size();
        }

        research.setMessage(Component.literal("Research: " + (packet.state().researchOnline() ? "ON" : "OFF")));
        oxygen.setMessage(Component.literal("Oxygen: " + (packet.state().oxygenOnline() ? "ON" : "OFF")));
    }

    private void changeDebriefPage(final int direction) {
        debriefPage += direction;
        updateControls();
        triggerImmediateNarration(true);
    }

    private void prepareDebrief(final MissionState.Snapshot state) {
        if (!state.equals(debriefState)) {
            debriefState = state;
            debriefPage = 0;
            debriefLines.clear();
            for (final String fact : MissionDebrief.facts(state))
                wrapDebrief(fact, TEXT);

            wrapDebrief("What changed", ACCENT);
            for (final String consequence : MissionDebrief.consequences(state))
                wrapDebrief(consequence, TEXT);

            wrapDebrief("The real mission", ACCENT);
            wrapDebrief(MissionDebrief.realStory(state), MUTED);
            wrapDebrief("Crew reflection", ACCENT);
            wrapDebrief(MissionDebrief.REFLECTION, TEXT);
        }

        debriefPage = Math.clamp(debriefPage, 0, Math.max(0, Math.ceilDiv(debriefLines.size(), debriefLinesPerPage) - 1));
    }

    private void wrapDebrief(final String paragraph, final Color color) {
        String line = "";
        for (final String word : paragraph.split("\\s+")) {
            final String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && NovaRenderer.width(candidate, 10.5F) > panelWidth - 28) {
                debriefLines.add(new DebriefLine(line, color));
                line = word;
            } else {
                line = candidate;
            }
        }

        if (!line.isEmpty())
            debriefLines.add(new DebriefLine(line, color));
    }

    @Override
    public @NonNull Component getNarrationMessage() {
        if (tab != DEBRIEF_TAB || debriefLines.isEmpty() || previousDebrief == null || !previousDebrief.visible)
            return super.getNarrationMessage();

        final StringBuilder narration = new StringBuilder("Mission debrief. Page ").append(debriefPage + 1)
                .append(" of ").append(Math.ceilDiv(debriefLines.size(), debriefLinesPerPage)).append(". ");
        for (int line = debriefPage * debriefLinesPerPage;
             line < Math.min(debriefLines.size(), (debriefPage + 1) * debriefLinesPerPage); line++)
            narration.append(debriefLines.get(line).text()).append(' ');

        return Component.literal(narration.toString());
    }

    private record DebriefLine(String text, Color color) {
        /* w */
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        updateControls();
        NovaRenderer.rect(graphics, 0, 0, width, height, 0, new Color(10, 15, 22, 155));
        MissionUi.panel(graphics, panelX, panelY, panelWidth, panelHeight);
        NovaRenderer.rect(graphics, panelX + 12, panelY + 10, 2, 23, 1, ACCENT);
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
                        MissionUi.text(graphics, state.decisions() + " equipment decisions  |  Radiation dose " + Math.round(state.radiationDose())
                                + "% / prototype game units", contentX, contentY + 143, state.radiationDose() >= 50 ? DANGER : MUTED);
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
                        for (int index = 0; index < debriefLinesPerPage
                                && debriefPage * debriefLinesPerPage + index < debriefLines.size(); index++) {
                            final DebriefLine line = debriefLines.get(debriefPage * debriefLinesPerPage + index);
                            NovaRenderer.text(graphics, line.text(), contentX, contentY + 18 + index * 14, 10.5F, line.color());
                        }

                        final String pageLabel = "Page " + (debriefPage + 1) + " of " + Math.ceilDiv(debriefLines.size(), debriefLinesPerPage);
                        NovaRenderer.text(graphics, pageLabel, panelX + (panelWidth - NovaRenderer.width(pageLabel, 9.5F)) / 2,
                                panelY + panelHeight - 53, 9.5F, MUTED);
                    }
                }
                default -> {
                    MissionUi.text(graphics, "Crew briefing", contentX, contentY, ACCENT);
                    final DustStorm storm = state.dustStorm();
                    MissionUi.wrap(graphics, "A dust storm starts dimming the sun at 01:15. By 02:15 only " + storm.peakPercent()
                                    + "% of the sunlight is left, and the dust starts to thin at 03:15. Keep battery and oxygen above zero "
                                    + "until 04:00; finish 90 seconds of research for full success. This storm is real: " + storm.summary()
                                    + " At 00:25 a solar storm alert gives you 15 seconds to reach the crew shelter, south of the habitat; "
                                    + "stay inside until 01:05.",
                            contentX, contentY + 18, contentWidth, panelHeight >= 290 ? 8 : 6, TEXT);

                    if (panelHeight >= 290) {
                        MissionUi.wrap(graphics, "Real data: the sunlight drop and dust opacity (tau: how much dust blocks the sun). "
                                        + "Game assumptions: battery size, energy use, about 10 sols (Mars days) squeezed into 1 minute, "
                                        + "the backup generator, and the recovery. Each reset may bring a different real storm. "
                                        + "The solar storm is inspired by September 2017, when NASA's Curiosity measured more than double "
                                        + "its earlier surface radiation record; its timing and dose units are made up.",
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
