package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.mission.EducationPause;
import cc.nimbusnova.mc.mission.DustStorm;
import cc.nimbusnova.mc.mission.MissionDebrief;
import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionProgressPayload;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.learning.LearningClient;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.hub.HubActionPayload;
import java.awt.Color;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.NonNull;

public final class MissionConsoleScreen extends NovaScreen implements MissionPalette {
    private int tab;
    private int panelX, panelY, panelWidth, panelHeight;
    private MissionButton start, pause, reset, oxygen, research, forecast, powerRole, lifeRole, weatherRole;
    private MissionButton previousDebrief, nextDebrief, previousLesson, nextLesson, lessonSource, crewCheck;
    private int lessonPage;
    private String lessonId = "";
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

        final String[] names = { "Mission", "Crew", "Science", "Report" };
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
        start = addRenderableWidget(new MissionButton(panelX + 12, footerY, controlWidth, 22, "Back to mission", () -> {
            final MissionProgressPayload objective = GuidedMissionClient.progress();
            if (!ObjectiveGuidance.onMars()) {
                if (minecraft.getConnection() != null)
                    minecraft.getConnection().sendCommand("mars join");
                onClose();
            } else if (NimbusNovaClient.snapshot != null && NimbusNovaClient.snapshot.operator()
                    && NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.READY && objective != null && objective.ready())
                command("start");
            else if (NimbusNovaClient.snapshot != null && NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.READY
                    && objective != null && objective.ready() && ClientPlayNetworking.canSend(HubActionPayload.TYPE))
                ClientPlayNetworking.send(new HubActionPayload("request_start", "mars"));
            else
                onClose();
        }));
        pause = addRenderableWidget(new MissionButton(panelX + 18 + controlWidth, footerY, controlWidth, 22, "Pause", () -> {
            if (NimbusNovaClient.snapshot == null) return;
            if (!ObjectiveGuidance.onMars() && NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.READY) {
                if (minecraft.getConnection() != null)
                    minecraft.getConnection().sendCommand("mars create");
            } else if (NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.PAUSED
                    && !NimbusNovaClient.snapshot.educationIncident().isEmpty() && !NimbusNovaClient.snapshot.educationConfirmed()) {
                if (LearningClient.solved("mars", NimbusNovaClient.snapshot.educationIncident())) command("acknowledge");
                else LearningClient.open("mars");
            } else
                command(NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.PAUSED ? "resume" : "pause");
        }));
        reset = addRenderableWidget(new MissionButton(panelX + 24 + controlWidth * 2, footerY, controlWidth, 22, "Restart", () -> command("reset")));
        previousDebrief = addRenderableWidget(new MissionButton(panelX + 14, footerY - 28, 78, 22,
                "Previous", () -> changeDebriefPage(-1)));
        nextDebrief = addRenderableWidget(new MissionButton(panelX + panelWidth - 92, footerY - 28, 78, 22,
                "Next", () -> changeDebriefPage(1)));
        previousLesson = addRenderableWidget(new MissionButton(panelX + 14, footerY - 28, 78, 22,
                "Previous lesson", () -> { lessonPage--; updateControls(); }));
        nextLesson = addRenderableWidget(new MissionButton(panelX + panelWidth - 92, footerY - 28, 78, 22,
                "Next lesson", () -> { lessonPage++; updateControls(); }));
        lessonSource = addRenderableWidget(new MissionButton(panelX + 104, footerY - 28, (panelWidth - 218) / 2, 22,
                "NASA source", () -> {
                    if (NimbusNovaClient.snapshot != null && !NimbusNovaClient.snapshot.educationIncident().isEmpty()) {
                        final var incident = EducationPause.Incident.valueOf(NimbusNovaClient.snapshot.educationIncident());
                        ConfirmLinkScreen.confirmLinkNow(this, URI.create(incident.source));
                    }
                }));

        crewCheck = addRenderableWidget(new MissionButton(panelX + 110 + (panelWidth - 218) / 2, footerY - 28,
                (panelWidth - 218) / 2, 22, "Crew check", () -> LearningClient.open("mars")));

        final int systemWidth = (panelWidth - 36) / 3;
        powerRole = addRenderableWidget(new MissionButton(panelX + 12, panelY + 94, systemWidth, 22, "Power engineer", () -> command("role power")));
        lifeRole = addRenderableWidget(new MissionButton(panelX + 18 + systemWidth, panelY + 94, systemWidth, 22, "Life support", () -> command("role life_support")));
        weatherRole = addRenderableWidget(new MissionButton(panelX + 24 + systemWidth * 2, panelY + 94, systemWidth, 22, "Weather officer", () -> command("role weather")));
        research = addRenderableWidget(new MissionButton(panelX + 12, panelY + 154, systemWidth, 24, "Research", () -> {
            if (NimbusNovaClient.snapshot != null)
                command("equipment research " + !NimbusNovaClient.snapshot.state().researchOnline());
        }));
        oxygen = addRenderableWidget(new MissionButton(panelX + 18 + systemWidth, panelY + 154, systemWidth, 24, "Oxygen", () -> {
            if (NimbusNovaClient.snapshot != null)
                command("equipment oxygen " + !NimbusNovaClient.snapshot.state().oxygenOnline());
        }));
        forecast = addRenderableWidget(new MissionButton(panelX + 24 + systemWidth * 2, panelY + 154, systemWidth, 24, "Send forecast", () -> command("forecast")));
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
        final MissionProgressPayload objective = GuidedMissionClient.progress();
        final boolean canLaunch = connected && phase == MissionState.Phase.READY && objective != null && objective.ready();
        start.setMessage(Component.literal(!ObjectiveGuidance.onMars() ? "Enter outpost"
                : canLaunch ? operator ? "Start mission" : "Ask teacher to start" : "Back to mission"));
        start.active = connected;
        final boolean prepareOutpost = !ObjectiveGuidance.onMars() && phase == MissionState.Phase.READY;
        pause.active = operator && (prepareOutpost || phase == MissionState.Phase.ACTIVE || phase == MissionState.Phase.PAUSED);
        final boolean lesson = connected && !packet.educationIncident().isEmpty();
        pause.setMessage(Component.literal(prepareOutpost ? "Prepare outpost" : phase != MissionState.Phase.PAUSED ? "Pause"
                : lesson && !packet.educationConfirmed() ? "Crew check" : "Resume"));
        if (connected && !packet.educationIncident().equals(lessonId)) {
            lessonId = packet.educationIncident();
            lessonPage = 0;
        }
        previousLesson.visible = nextLesson.visible = lesson && tab == 2;
        lessonSource.visible = lesson && tab == 2 && lessonPage == 2;
        crewCheck.visible = lesson && tab == 2;
        previousLesson.active = lessonPage > 0;
        nextLesson.active = lessonPage < 2;
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
        NovaRenderer.text(graphics, "The Darkening Sky", panelX + 20, panelY + 8, 15, TEXT);
        MissionUi.text(graphics, "Your crew / one shared mission", panelX + 20, panelY + 29, MUTED);

        final MissionSnapshotPayload packet = NimbusNovaClient.snapshot;
        final int contentX = panelX + 14, contentY = panelY + 78, contentWidth = panelWidth - 28;
        if (packet == null) {
            MissionUi.wrap(graphics, "Waiting for the mission server. Join a server with Nimbus Nova installed.",
                    contentX, contentY, contentWidth, 4, TEXT);
        } else {
            final MissionState.Snapshot state = packet.state();
            switch (tab) {
                case 0 -> {
                    final MissionProgressPayload objective = GuidedMissionClient.progress();
                    final String title = !ObjectiveGuidance.onMars() ? "Enter Aster Outpost"
                            : objective == null ? "Meet Commander Vega" : objective.title();
                    NovaRenderer.text(graphics, title, contentX, contentY - 2,
                            NovaRenderer.width(title, 15) > contentWidth ? 12 : 15, TEXT);
                    final String instruction = !ObjectiveGuidance.onMars()
                            ? packet.operator() ? "Prepare the outpost if needed, then choose Enter outpost. Your crew's first task waits on Mars."
                            : "Choose Enter outpost below to join your crew on Mars."
                            : objective == null ? "Walk into the habitat and right-click Commander Vega. He will give your crew its first task."
                            : objective.instruction();
                    NovaRenderer.wrap(graphics, instruction, contentX, contentY + 23, contentWidth, 10.5F, 2, TEXT);
                    ObjectiveGuidance.arrow(graphics, objective, contentX + 5, contentY + 62, ACCENT);
                    NovaRenderer.wrap(graphics, objective == null ? "Right-click the astronaut to begin" : ObjectiveGuidance.navigation(objective),
                            contentX + 18, contentY + 55, contentWidth - 18, 9.5F, 1, ACCENT);
                    final int completed = objective == null ? 0 : objective.completed();
                    final int total = objective == null ? 5 : Math.max(1, objective.total());
                    NovaRenderer.text(graphics, state.phase() == MissionState.Phase.READY
                                    ? "Crew preparation: " + completed + " / " + total + " tasks complete"
                                    : "Science progress: " + Math.min(state.researchSeconds(), 90) + " / 90 seconds",
                            contentX, contentY + 75, 9, MUTED);
                    MissionUi.bar(graphics, contentX, contentY + 89, contentWidth,
                            state.phase() == MissionState.Phase.READY ? completed * 100.0 / total : state.researchSeconds() * 100.0 / 90, SUCCESS);
                    final String feedback = objective == null || objective.feedback().isBlank()
                            ? "The clock stays stopped while your crew prepares." : objective.feedback();
                    if (panelHeight >= 290) {
                        NovaRenderer.wrap(graphics, MissionTutorial.job(packet.role()), contentX, contentY + 109, contentWidth, 11, 3, ACCENT);
                        NovaRenderer.text(graphics, "Keep power and air above zero. Finish the science objective together.", contentX, contentY + 158, 10, TEXT);
                    } else {
                        NovaRenderer.wrap(graphics, feedback, contentX, contentY + 100, contentWidth, 9, 1, MUTED);
                    }
                }
                case 1 -> {
                    MissionUi.text(graphics, "Choose your crew responsibility", contentX, contentY, TEXT);
                    NovaRenderer.wrap(graphics, MissionTutorial.job(packet.role()), contentX, contentY + 45, contentWidth, 10, 2, ACCENT);
                    if (panelHeight >= 290) {
                        NovaRenderer.wrap(graphics, "Repairs happen at the marked equipment in the world. During the timed mission, "
                                        + "these switches let each specialist manage their system. Talk before changing the shared power budget.",
                                contentX, contentY + 116, contentWidth, 10, 4, MUTED);
                        MissionUi.text(graphics, "Emergency air supplies: " + state.canistersLeft() + " remaining", contentX, contentY + 175, OXYGEN);
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
                    if (!packet.educationIncident().isEmpty()) {
                        final EducationPause.Incident incident = EducationPause.Incident.valueOf(packet.educationIncident());
                        MissionUi.text(graphics, incident.title, contentX, contentY, ACCENT);
                        if (lessonPage == 0) {
                            MissionUi.wrap(graphics, incident.observation, contentX, contentY + 20, contentWidth, 2, TEXT);
                            MissionUi.wrap(graphics, incident.explanation, contentX, contentY + 47, contentWidth, 3, TEXT);
                        } else if (lessonPage == 1) {
                            MissionUi.wrap(graphics, incident.hazard, contentX, contentY + 20, contentWidth, 3, TEXT);
                            MissionUi.wrap(graphics, incident.question, contentX, contentY + 58, contentWidth, 2, ACCENT);
                        } else {
                            MissionUi.wrap(graphics, incident.evidence, contentX, contentY + 20, contentWidth, 4, TEXT);
                            MissionUi.wrap(graphics, "Open the source or ask Mission Control to share it.",
                                    contentX, contentY + 72, contentWidth, 2, MUTED);
                        }
                        break;
                    }
                    MissionUi.text(graphics, "Crew briefing", contentX, contentY, ACCENT);
                    final DustStorm storm = state.dustStorm();
                    MissionUi.wrap(graphics, "A dust storm starts dimming the sun at 01:15. By 02:15 only " + storm.peakPercent()
                                    + "% of the sunlight is left, and the dust starts to thin at 03:15. Keep battery and oxygen above zero "
                                    + "until 04:00; finish 90 seconds of research for full success. This storm is real: " + storm.summary()
                                    + " At 00:10 a solar storm alert gives you 30 seconds to reach the crew shelter, south of the habitat; "
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
