package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.mission.MissionPreparation;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.mission.MissionTaskActionPayload;
import cc.nimbusnova.mc.mission.MissionTaskOpenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class TaskScreen extends NovaScreen implements MissionPalette {
    private MissionTaskOpenPayload state;
    private final List<Button> controls = new ArrayList<>();
    private final float[] cableAngles = new float[9], cableTargets = new float[9];
    private final float[] valveAngles = new float[3];
    private boolean awaitingServer;
    private int left, top, panelWidth, panelHeight, cellSize, boardX, boardY, boardWidth;
    private int selectedCell = -1, selectedValve = -1;
    private long lastFrame = System.nanoTime(), completedAt;
    private double animationTime;
    private @Nullable MissionButton activate;

    public TaskScreen(final MissionTaskOpenPayload state) {
        super(Component.literal(state.kind() == MissionPreparation.TaskKind.POWER ? "Restore the power circuit" : "Balance the oxygen recycler"));
        this.state = state;
        for (int cell = 0; cell < 9; cell++)
            cableAngles[cell] = cableTargets[cell] = (state.puzzleState() >> cell * 2 & 3) * 90;

        for (int valve = 0; valve < 3; valve++)
            valveAngles[valve] = -135 + MissionPreparation.oxygenValve(state.puzzleState(), valve) * 67.5F;

        if (state.step() < 0)
            completedAt = System.nanoTime();
    }

    public MissionTaskOpenPayload serverState() {
        return state;
    }

    public void acceptState(final MissionTaskOpenPayload packet) {
        if (packet.kind() != state.kind()) return;
        if (packet.kind() == MissionPreparation.TaskKind.POWER) {
            for (int cell = 0; cell < 9; cell++) {
                final int previous = state.puzzleState() >> cell * 2 & 3, next = packet.puzzleState() >> cell * 2 & 3;
                cableTargets[cell] += Math.floorMod(next - previous, 4) * 90;
            }
        }

        final boolean completed = packet.step() < 0 && state.step() >= 0;
        state = packet;
        awaitingServer = false;
        if (completed) {
            completedAt = System.nanoTime();
            rebuildWidgets();
            triggerImmediateNarration(true);
        }

        refreshControls();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(500, width - 16);
        panelHeight = Math.min(310, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        controls.clear();
        activate = null;
        if (state.step() < 0) {
            addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 42, panelWidth - 28, 26, "Next task", this::onClose));
        } else if (state.kind() == MissionPreparation.TaskKind.POWER) {
            cellSize = Math.min(60, Math.min((panelHeight - 98) / 3, (panelWidth - 192) / 3));
            boardWidth = cellSize * 3 + 8;
            boardX = left + 53;
            boardY = top + 55;
            for (int cell = 0; cell < 9; cell++)
                controls.add(addRenderableWidget(new CircuitTile(boardX + cell % 3 * (cellSize + 4),
                        boardY + cell / 3 * (cellSize + 4), cellSize, cell)));

        } else {
            final String[] labels = { "intake", "pump", "vent" };
            final int spacing = (panelWidth - 28) / 3;
            for (int valve = 0; valve < 3; valve++) {
                final int index = valve, centerX = left + 14 + spacing * valve + spacing / 2;
                controls.add(addRenderableWidget(new ValveDial(centerX - 18, top + 108, index, labels[valve])));
                controls.add(addRenderableWidget(new AdjustmentButton(centerX - 30, top + 147, "Decrease " + labels[valve], "-", () -> submit(index * 2 + 1))));
                controls.add(addRenderableWidget(new AdjustmentButton(centerX + 8, top + 147, "Increase " + labels[valve], "+", () -> submit(index * 2))));
            }

            activate = addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 36, panelWidth - 28, 24,
                    "Start recycler", () -> submit(6)));
        }

        addRenderableWidget(new MissionButton(left + panelWidth - 29, top + 10, 18, 18, "X", this::onClose));
        refreshControls();
    }

    private void submit(final int choice) {
        if (awaitingServer || state.step() < 0 || !ClientPlayNetworking.canSend(MissionTaskActionPayload.TYPE)) return;
        if (state.kind() == MissionPreparation.TaskKind.POWER)
            selectedCell = choice;
        else if (choice < 6)
            selectedValve = choice / 2;
        awaitingServer = true;
        refreshControls();
        ClientPlayNetworking.send(new MissionTaskActionPayload(state.kind(), state.step(), choice));
    }

    void syncProgress() {
        refreshControls();
    }

    boolean canOperate() {
        final var mission = NimbusNovaClient.missionSnapshot();
        return mission != null && (mission.operator() || mission.role().canControl(state.kind() == MissionPreparation.TaskKind.POWER
                ? MissionState.Equipment.RESEARCH : MissionState.Equipment.OXYGEN));
    }

    private void refreshControls() {
        final var mission = NimbusNovaClient.missionSnapshot();
        final boolean enabled = !awaitingServer && state.step() >= 0 && canOperate() && mission != null
                && (mission.state().phase() == MissionState.Phase.READY
                || mission.state().phase() == MissionState.Phase.ACTIVE);
        for (final Button button : controls)
            button.active = enabled;

        if (activate != null)
            activate.active = enabled && (state.aux() >>> 16) >= MissionPreparation.OXYGEN_STABLE_TICKS;
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        final long now = System.nanoTime();
        final float elapsed = Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        final var mission = NimbusNovaClient.missionSnapshot();
        final boolean motion = minecraft.options.screenEffectScale().get() > 0
                && (mission == null || mission.state().phase() != MissionState.Phase.PAUSED);
        if (motion)
            animationTime += elapsed;
        for (int index = 0; index < 9; index++)
            cableAngles[index] = motion ? cableAngles[index] + (cableTargets[index] - cableAngles[index]) * Math.min(1, elapsed * 18) : cableTargets[index];

        MissionUi.panel(graphics, left, top, panelWidth, panelHeight);
        if (state.step() < 0) {
            drawComplete(graphics, now, motion);
        } else {
            NovaRenderer.title(graphics, left, top, panelWidth,
                    state.kind() == MissionPreparation.TaskKind.POWER ? "Route solar power" : "Balance the oxygen recycler");
            NovaRenderer.text(graphics, mission != null && mission.state().phase() == MissionState.Phase.PAUSED
                            ? "Mission paused. Your puzzle progress is safe."
                            : state.kind() == MissionPreparation.TaskKind.POWER ? "Turn the cables to connect SOLAR to BASE."
                            : "Turn the valves. Hold both gauges green for 2 seconds.",
                    left + 14, top + 34, 9, MUTED);
            if (state.kind() == MissionPreparation.TaskKind.POWER)
                drawCircuit(graphics);
            else
                drawOxygen(graphics, elapsed, motion);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawCircuit(final GuiGraphicsExtractor graphics) {
        NovaRenderer.slot(graphics, boardX - 6, boardY - 6, boardWidth + 12, boardWidth + 12, new Color(20, 18, 29), false);
        final int terminalY = boardY + cellSize + 4 + cellSize / 2;
        NovaRenderer.rect(graphics, boardX - 16, terminalY - 5, 16, 10, ACCENT);
        NovaRenderer.text(graphics, "SOLAR", boardX - 10 - NovaRenderer.width("SOLAR", 8), terminalY - 17, 8, ACCENT);
        NovaRenderer.rect(graphics, boardX + boardWidth, terminalY - 5, 12, 10, BORDER);
        NovaRenderer.text(graphics, "BASE", boardX + boardWidth + 10, terminalY - 17, 8, MUTED);
        final int infoX = boardX + boardWidth + 40, infoWidth = left + panelWidth - infoX - 14;
        NovaRenderer.text(graphics, "Make a live path", infoX, boardY + 3, 10, ACCENT);
        NovaRenderer.wrap(graphics, "Click a tile to rotate its cable. Lit cables carry power from the solar input.",
                infoX, boardY + 24, infoWidth, 9, 5, TEXT);
        NovaRenderer.text(graphics, Integer.bitCount(state.aux() & 511) + " tiles energized", infoX, boardY + 91, 8, SUCCESS);
        NovaRenderer.wrap(graphics, selectedCell < 0 ? "Try the middle-left tile first." : "Tile " + (selectedCell + 1) + " selected. Keep the path connected.",
                infoX, boardY + 107, infoWidth, 8, 3, MUTED);
        final String feedback = awaitingServer ? "Turning cable..." : GuidedMissionClient.progress() == null ? "" : GuidedMissionClient.progress().feedback();
        NovaRenderer.wrap(graphics, feedback, left + 14, top + panelHeight - 23, panelWidth - 28, 8, 1, MUTED);
    }

    private void drawOxygen(final GuiGraphicsExtractor graphics, final float elapsed, final boolean motion) {
        final int packed = state.puzzleState(), pressure = MissionPreparation.oxygenPressure(packed), flow = MissionPreparation.oxygenFlow(packed);
        gauge(graphics, "Pressure", pressure, MissionPreparation.OXYGEN_PRESSURE_MIN, MissionPreparation.OXYGEN_PRESSURE_MAX, top + 49);
        gauge(graphics, "Flow", flow, MissionPreparation.OXYGEN_FLOW_MIN, MissionPreparation.OXYGEN_FLOW_MAX, top + 73);
        final int spacing = (panelWidth - 28) / 3;
        final String[] names = { "Intake", "Pump", "Vent" };
        for (int valve = 0; valve < 3; valve++) {
            final float target = -135 + MissionPreparation.oxygenValve(packed, valve) * 67.5F;
            valveAngles[valve] = motion ? valveAngles[valve] + (target - valveAngles[valve]) * Math.min(1, elapsed * 14) : target;
            final int centerX = left + 14 + spacing * valve + spacing / 2;
            final String label = names[valve] + " " + MissionPreparation.oxygenValve(packed, valve) + "/4";
            NovaRenderer.text(graphics, label, centerX - NovaRenderer.width(label, 8) / 2, top + 98, 8, selectedValve == valve ? ACCENT : MUTED);
        }

        final float stable = Math.clamp((state.aux() >>> 16) / (float) MissionPreparation.OXYGEN_STABLE_TICKS, 0, 1);
        final int progressY = top + panelHeight - 47;
        NovaRenderer.meter(graphics, left + 14, progressY, panelWidth - 95, 5, stable, SUCCESS);
        NovaRenderer.text(graphics, stable >= 1 ? "Stable!" : "Hold steady", left + panelWidth - 72, progressY - 4, 8, stable >= 1 ? SUCCESS : MUTED);
    }

    private void gauge(final GuiGraphicsExtractor graphics, final String name, final int value, final int minimum, final int maximum, final int y) {
        final int x = left + 14, width = panelWidth - 28;
        final boolean safe = value >= minimum && value <= maximum;
        NovaRenderer.text(graphics, name + " " + value, x, y, 9, safe ? SUCCESS : TEXT);
        NovaRenderer.text(graphics, "Target " + minimum + "-" + maximum,
                x + width - NovaRenderer.width("Target " + minimum + "-" + maximum, 8), y, 8, MUTED);
        NovaRenderer.slot(graphics, x, y + 14, width, 6, SURFACE, false);
        NovaRenderer.rect(graphics, x + width * minimum / 100F, y + 12, width * (maximum - minimum) / 100F, 10, new Color(113, 192, 143, 100));
        NovaRenderer.rect(graphics, x + width * Math.clamp(value, 0, 100) / 100F - 1, y + 11, 2, 12, safe ? SUCCESS : ACCENT);
    }

    private void drawComplete(final GuiGraphicsExtractor graphics, final long now, final boolean motion) {
        final float age = Math.clamp((now - completedAt) / 700_000_000F, 0, 1);
        if (motion && age < 1)
            NovaRenderer.stroke(graphics, left + 6, top + 6, panelWidth - 12, panelHeight - 12, 2,
                    new Color(158, 232, 114, Math.round((1 - age) * 130)));

        NovaRenderer.title(graphics, left, top, panelWidth, state.kind() == MissionPreparation.TaskKind.POWER ? "Circuit connected!" : "Recycler ready!");
        NovaRenderer.wrap(graphics, state.kind() == MissionPreparation.TaskKind.POWER
                        ? "Power now reaches the habitat. Your circuit repair is complete."
                        : "Pressure and flow are stable. Your oxygen system is ready for the crew.",
                left + 20, top + 68, panelWidth - 40, 12, 3, TEXT);
        NovaRenderer.text(graphics, "Follow the next marker to help your crew.", left + 20, top + panelHeight - 77, 10, MUTED);
    }

    @Override
    public Component getNarrationMessage() {
        if (state.step() < 0) return Component.literal(getTitle().getString() + ". Complete. Choose Next task to continue.");
        return Component.literal(state.kind() == MissionPreparation.TaskKind.POWER
                ? "Rotate the cable tiles. Connect solar input on the left of the middle row to the base on the right."
                : "Adjust intake, pump and vent. Pressure target 55 to 65. Flow target 35 to 45. Hold both steady for two seconds, then start the recycler.");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class CircuitTile extends Button {
        private final int index;

        private CircuitTile(final int x, final int y, final int size, final int index) {
            super(x, y, size, size, Component.literal("Rotate tile " + (index + 1)), _ -> submit(index), DEFAULT_NARRATION);
            this.index = index;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            final boolean live = (state.aux() & 1 << index) != 0 && Math.abs(cableTargets[index] - cableAngles[index]) < 5;
            final boolean selected = isHoveredOrFocused() || selectedCell == index;
            NovaRenderer.slot(graphics, getX(), getY(), getWidth(), getHeight(), SURFACE, selected);
            final Color cable = live ? ACCENT : new Color(105, 126, 138);
            graphics.pose().pushMatrix();
            graphics.pose().translate(getX() + getWidth() / 2F, getY() + getHeight() / 2F);
            graphics.pose().rotate((float) Math.toRadians(cableAngles[index]));
            final int mask = MissionPreparation.powerConnections(index, 0);
            for (int direction = 0; direction < 4; direction++) {
                if ((mask & 1 << direction) == 0) continue;
                graphics.pose().pushMatrix();
                graphics.pose().rotate(direction * (float) Math.PI / 2);
                NovaRenderer.rect(graphics, -2.4F, -getHeight() / 2F - 2, 4.8F, getHeight() / 2F + 2, cable);
                if (live && minecraft.options.screenEffectScale().get() > 0) {
                    final float travel = (float) ((animationTime * 1.25 + index * 0.09) % 1);
                    NovaRenderer.rect(graphics, -1.5F, -travel * getHeight() / 2F - 1.5F, 3, 3, TEXT);
                }
                graphics.pose().popMatrix();
            }

            NovaRenderer.rect(graphics, -4, -4, 8, 8, cable);
            graphics.pose().popMatrix();
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            final int mask = MissionPreparation.powerConnections(index, state.puzzleState());
            return Component.literal("Rotate tile " + (index + 1) + ", row " + (index / 3 + 1) + ", column " + (index % 3 + 1)
                    + ". Connectors " + ((mask & 1) != 0 ? "up " : "") + ((mask & 2) != 0 ? "right " : "")
                    + ((mask & 4) != 0 ? "down " : "") + ((mask & 8) != 0 ? "left " : ""));
        }
    }

    private final class ValveDial extends Button {
        private final int index;

        private ValveDial(final int x, final int y, final int index, final String name) {
            super(x, y, 36, 36, Component.literal("Increase " + name), _ -> submit(index * 2), DEFAULT_NARRATION);
            this.index = index;
        }

        @Override
        public void onPress(final InputWithModifiers input) {
            submit(index * 2 + (minecraft.hasShiftDown() ? 1 : 0));
        }

        @Override
        protected boolean isValidClickButton(final MouseButtonInfo button) {
            return button.button() == 0 || button.button() == 1;
        }

        @Override
        public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
            submit(index * 2 + (event.button() == 1 || minecraft.hasShiftDown() ? 1 : 0));
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            final float centerX = getX() + 18, centerY = getY() + 18;
            final boolean highlighted = isHoveredOrFocused() || selectedValve == index;
            NovaRenderer.slot(graphics, getX(), getY(), 36, 36, SURFACE, highlighted);
            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY);
            graphics.pose().rotate((float) Math.toRadians(valveAngles[index]));
            NovaRenderer.rect(graphics, -1.2F, -13, 2.4F, 14, OXYGEN);
            graphics.pose().popMatrix();
            NovaRenderer.rect(graphics, centerX - 3, centerY - 3, 6, 6, OXYGEN);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.literal(getMessage().getString() + ". Valve level " + MissionPreparation.oxygenValve(state.puzzleState(), index)
                    + " of four. Activate to open; shift-activate or right-click to close.");
        }
    }

    private static final class AdjustmentButton extends Button {
        private final String symbol;

        private AdjustmentButton(final int x, final int y, final String label, final String symbol, final Runnable action) {
            super(x, y, 22, 18, Component.literal(label), _ -> action.run(), DEFAULT_NARRATION);
            this.symbol = symbol;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            NovaRenderer.button(graphics, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), false);
            NovaRenderer.centered(graphics, symbol, getX(), getY(), getWidth(), getHeight(), active ? TEXT : MUTED);
        }
    }
}
