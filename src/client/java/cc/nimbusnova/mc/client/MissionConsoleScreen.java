package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.MissionState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MissionConsoleScreen extends Screen implements MissionPalette {
    private int tab;
    private int panelX, panelY, panelWidth, panelHeight;
    private MissionButton start, pause, reset, oxygen, research, powerRole, lifeRole;

    public MissionConsoleScreen() {
        super(Component.literal("Nimbus Nova mission console"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, width - 16);
        panelHeight = Math.min(330, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        final String[] names = { "Overview", "Systems", "Briefing" };
        final int tabWidth = (panelWidth - 36) / 3;
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

        final int systemWidth = (panelWidth - 30) / 2;
        powerRole = addRenderableWidget(new MissionButton(panelX + 12, panelY + 94, systemWidth, 22, "Power engineer", () -> command("role power")));
        lifeRole = addRenderableWidget(new MissionButton(panelX + 18 + systemWidth, panelY + 94, systemWidth, 22, "Life support", () -> command("role life_support")));
        research = addRenderableWidget(new MissionButton(panelX + 12, panelY + 144, systemWidth, 24, "Research", () -> {
            if (NimbusNovaClient.snapshot != null)
                command("equipment research " + !NimbusNovaClient.snapshot.state().researchOnline());
        }));
        oxygen = addRenderableWidget(new MissionButton(panelX + 18 + systemWidth, panelY + 144, systemWidth, 24, "Oxygen", () -> {
            if (NimbusNovaClient.snapshot != null)
                command("equipment oxygen " + !NimbusNovaClient.snapshot.state().oxygenOnline());
        }));
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
        powerRole.visible = lifeRole.visible = oxygen.visible = research.visible = tab == 1 && connected;
        powerRole.active = lifeRole.active = connected && phase == MissionState.Phase.READY;
        powerRole.selected = connected && packet.role() == CrewRole.POWER;
        lifeRole.selected = connected && packet.role() == CrewRole.LIFE_SUPPORT;
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
                            + "/90s  |  " + MissionUi.rate(state.generation() - state.demand()) + " energy/s", contentX, contentY + 52, MUTED);
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
                    MissionUi.text(graphics, "Habitat equipment", contentX, contentY + 49, TEXT);
                    MissionUi.wrap(graphics, "Research uses 0.65 energy/s. Oxygen uses 0.45 energy/s.", contentX,
                            contentY + 100, contentWidth, Math.max(1, (panelHeight - 210) / 11), MUTED);

                    if (panelHeight >= 290) {
                        MissionUi.wrap(graphics, "Power engineers control research demand. Life-support engineers control oxygen production. "
                                        + "Mission Control can operate both for solo testing. Choose roles before starting.",
                                contentX, contentY + 134, contentWidth, 4, TEXT);
                    }
                }
                default -> {
                    MissionUi.text(graphics, "Crew briefing", contentX, contentY, ACCENT);
                    MissionUi.wrap(graphics, "A dust front will reduce solar supply at 01:15 and clear at 03:15. "
                                    + "Coordinate research power with oxygen production. Keep both reserves above zero until 04:00; "
                                    + "earn full success by completing 90 seconds of research.",
                            contentX, contentY + 18, contentWidth, panelHeight >= 290 ? 7 : 6, TEXT);

                    if (panelHeight >= 290) {
                        MissionUi.wrap(graphics, "Prototype simulation: all rates and event timing are authored game balance. "
                                        + "NASA measurements are not integrated yet. Surviving without the research target earns partial success.",
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
