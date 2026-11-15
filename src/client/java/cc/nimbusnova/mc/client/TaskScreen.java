package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.client.render.NovaSounds;
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
    private static final String[] VALVE_NAMES = { "Intake", "Pump", "Vent" };
    private static final String[][] VALVE_EFFECTS = { { "Pressure +10", "Flow +5" }, { "Pressure +10", "Flow +10" }, { "Pressure -5", "Flow -5" } };
    private MissionTaskOpenPayload state;
    private final List<Button> controls = new ArrayList<>();
    private final float[] cableAngles = new float[9], cableTargets = new float[9];
    private final float[] valveAngles = new float[3];
    private boolean awaitingServer;
    private int left, top, panelWidth, panelHeight, cellSize, boardX, boardY, boardWidth, valveTop, valveWidth;
    private int selectedCell = -1, selectedValve = -1, turns;
    private long lastFrame = System.nanoTime(), completedAt;
    private double animationTime;
    private float pressureNeedle, flowNeedle;
    private @Nullable MissionButton activate;

    public TaskScreen(final MissionTaskOpenPayload state) {
        super(Component.literal(state.kind() == MissionPreparation.TaskKind.POWER ? "Restore the power circuit" : "Balance the oxygen recycler"));
        this.state = state;
        for (int cell = 0; cell < 9; cell++)
            cableAngles[cell] = cableTargets[cell] = (state.puzzleState() >> cell * 2 & 3) * 90;

        for (int valve = 0; valve < 3; valve++)
            valveAngles[valve] = -135 + MissionPreparation.oxygenValve(state.puzzleState(), valve) * 67.5F;

        pressureNeedle = MissionPreparation.oxygenPressure(state.puzzleState());
        flowNeedle = MissionPreparation.oxygenFlow(state.puzzleState());
        if (state.step() < 0)
            completedAt = System.nanoTime();
    }

    public MissionTaskOpenPayload serverState() {
        return state;
    }

    public void acceptState(final MissionTaskOpenPayload packet) {
        if (packet.kind() != state.kind()) return;
        final boolean power = packet.kind() == MissionPreparation.TaskKind.POWER, completed = packet.step() < 0 && state.step() >= 0;
        if (power) {
            for (int cell = 0; cell < 9; cell++) {
                final int previous = state.puzzleState() >> cell * 2 & 3, next = packet.puzzleState() >> cell * 2 & 3;
                cableTargets[cell] += Math.floorMod(next - previous, 4) * 90;
            }
        }

        if (completed)
            NovaSounds.success();
        else if (packet.puzzleState() != state.puzzleState()) {
            turns++;
            if (power && Integer.bitCount(packet.aux() & 511) > Integer.bitCount(state.aux() & 511)
                    || !power && MissionPreparation.oxygenBalanced(packet.puzzleState()) && !MissionPreparation.oxygenBalanced(state.puzzleState()))
                NovaSounds.good();
            else
                NovaSounds.step(power ? 1.2F : 0.8F + 0.15F * Math.max(0, selectedValve));
        }

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

        final boolean power = state.kind() == MissionPreparation.TaskKind.POWER;
        cellSize = Math.clamp(Math.min((panelHeight - 86) / 3, (panelWidth - 237) / 3), 26, 60);
        boardWidth = cellSize * 3 + 8;
        boardX = left + 52;
        boardY = top + 42;
        valveWidth = (panelWidth - 40) / 3;
        valveTop = top + 84;
        if (state.step() < 0) {
            addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 34, panelWidth - 28, 22, "Next task", this::onClose)
                    .withTone(NovaRenderer.Tone.SUCCESS));
        } else if (power) {
            for (int cell = 0; cell < 9; cell++)
                controls.add(addRenderableWidget(new CircuitTile(boardX + cell % 3 * (cellSize + 4),
                        boardY + cell / 3 * (cellSize + 4), cellSize, cell)));

        } else {
            for (int valve = 0; valve < 3; valve++) {
                final int index = valve, cardX = left + 14 + valve * (valveWidth + 6);
                final String name = VALVE_NAMES[valve].toLowerCase();
                controls.add(addRenderableWidget(new ValveDial(cardX + 6, valveTop + 7, index, name)));
                controls.add(addRenderableWidget(new AdjustmentButton(cardX + 6, valveTop + 50, (valveWidth - 16) / 2, "Decrease " + name, "-", () -> submit(index * 2 + 1))));
                controls.add(addRenderableWidget(new AdjustmentButton(cardX + 10 + (valveWidth - 16) / 2, valveTop + 50, (valveWidth - 16) / 2, "Increase " + name, "+", () -> submit(index * 2))));
            }

            activate = addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 34, panelWidth - 28, 22,
                    "Start recycler", () -> submit(6)).withTone(NovaRenderer.Tone.SUCCESS));
        }

        addRenderableWidget(new MissionButton(left + panelWidth - 27, top + 8, 18, 18, "X", this::onClose));
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
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (state.kind() != MissionPreparation.TaskKind.OXYGEN || state.step() < 0 || scrollY == 0
                || mouseY < valveTop || mouseY > valveTop + 72) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        final int valve = (int) ((mouseX - left - 14) / (valveWidth + 6));
        if (valve < 0 || valve > 2) return false;
        submit(valve * 2 + (scrollY > 0 ? 0 : 1));
        return true;
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

        final boolean power = state.kind() == MissionPreparation.TaskKind.POWER, complete = state.step() < 0;
        MissionUi.panel(graphics, left, top, panelWidth, panelHeight);
        NovaRenderer.title(graphics, left, top, panelWidth, complete ? power ? "Circuit connected!" : "Recycler ready!"
                : power ? "Route solar power" : "Balance the oxygen recycler");
        if (complete && motion && now - completedAt < 900_000_000L)
            NovaRenderer.stroke(graphics, left + 3, top + 3, panelWidth - 6, panelHeight - 6, 2,
                    NovaRenderer.alpha(SUCCESS, Math.round((1 - (now - completedAt) / 900_000_000F) * 200)));

        if (power)
            drawCircuit(graphics, complete, mission != null && mission.state().phase() == MissionState.Phase.PAUSED);
        else
            drawOxygen(graphics, elapsed, motion, complete, mission != null && mission.state().phase() == MissionState.Phase.PAUSED);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawCircuit(final GuiGraphicsExtractor graphics, final boolean complete, final boolean paused) {
        NovaRenderer.slot(graphics, boardX - 5, boardY - 5, boardWidth + 10, boardWidth + 10, new Color(10, 22, 26), false);
        final int terminalY = boardY + cellSize + 4 + cellSize / 2;
        final float sun = 0.6F + 0.4F * (float) Math.sin(animationTime * 2.4);

        NovaRenderer.rect(graphics, boardX - 12, terminalY - 2, 12, 5, ACCENT);
        NovaRenderer.rect(graphics, boardX - 12, terminalY - 2, 12, 1, new Color(255, 240, 190));
        NovaRenderer.rect(graphics, boardX - 37, terminalY - 11, 26, 22, new Color(5, 7, 13));
        NovaRenderer.rect(graphics, boardX - 36, terminalY - 10, 24, 20, new Color(30, 62, 120));
        for (int line = 1; line < 3; line++) {
            NovaRenderer.rect(graphics, boardX - 36 + line * 8, terminalY - 10, 1, 20, new Color(104, 150, 220));
            NovaRenderer.rect(graphics, boardX - 36, terminalY - 10 + line * 7 - 1, 24, 1, new Color(104, 150, 220));
        }
        NovaRenderer.rect(graphics, boardX - 36, terminalY - 10, 24, 20, NovaRenderer.alpha(ACCENT, Math.round(40 * sun)));
        NovaRenderer.text(graphics, "SOLAR", boardX - 37, terminalY - 22, 8, ACCENT);

        final Color base = complete ? ACCENT : new Color(74, 90, 122);
        final int baseX = boardX + boardWidth + 5;
        NovaRenderer.rect(graphics, baseX, terminalY - 2, 8, 5, complete ? ACCENT : new Color(70, 88, 110));
        NovaRenderer.rect(graphics, baseX + 12, terminalY - 9, 12, 3, base);
        NovaRenderer.rect(graphics, baseX + 9, terminalY - 6, 18, 4, base);
        NovaRenderer.rect(graphics, baseX + 7, terminalY - 2, 22, 12, base);
        NovaRenderer.rect(graphics, baseX + 11, terminalY + 1, 5, 5, complete ? new Color(255, 248, 214) : new Color(20, 26, 40));
        NovaRenderer.rect(graphics, baseX + 20, terminalY + 3, 5, 7, new Color(20, 26, 40));
        NovaRenderer.text(graphics, "BASE", baseX + 8, terminalY - 22, 8, complete ? ACCENT : MUTED);

        if (complete) {
            for (int cell = 0; cell < 9; cell++)
                drawTile(graphics, boardX + cell % 3 * (cellSize + 4), boardY + cell / 3 * (cellSize + 4), cellSize, cell, false, true);
        }

        final int infoX = boardX + boardWidth + 47, infoWidth = left + panelWidth - infoX - 14, live = Integer.bitCount(state.aux() & 511);
        if (complete) {
            NovaRenderer.text(graphics, "POWER IS FLOWING", infoX, boardY - 2, 10, SUCCESS);
            NovaRenderer.wrap(graphics, "The habitat has light, heat and air pumps again." + (turns > 0 ? " Solved in " + turns + " turns." : ""),
                    infoX, boardY + 11, infoWidth, 8, 3, TEXT);
            NovaRenderer.callout(graphics, infoX, boardY + 48, infoWidth, Math.min(boardWidth - 50, 60), "SPACE FACT",
                    "The Space Station has about 13 km of wiring to carry solar power to every module.", ACCENT);
            return;
        }

        NovaRenderer.text(graphics, "Connect SOLAR to BASE", infoX, boardY - 2, 10, ACCENT);
        NovaRenderer.wrap(graphics, paused ? "Mission paused. Your puzzle progress is safe."
                : "Click a tile to turn its cable. Power only flows where cable ends touch.", infoX, boardY + 11, infoWidth, 8, 3, TEXT);
        NovaRenderer.text(graphics, "Live cables", infoX, boardY + 48, 8, MUTED);
        NovaRenderer.text(graphics, live + "/9", infoX + infoWidth - NovaRenderer.width(live + "/9", 8), boardY + 48, 8, live > 0 ? ACCENT : MUTED);
        NovaRenderer.meter(graphics, infoX, boardY + 58, infoWidth, 6, live / 9.0, ACCENT);
        if (boardWidth >= 112)
            NovaRenderer.callout(graphics, infoX, boardY + 70, infoWidth, Math.min(boardWidth - 68, 60), "WHY IT MATTERS",
                    "Solar panels are the outpost's only power. One broken link means no heat or air.", SUCCESS);

        final var progress = GuidedMissionClient.progress();
        NovaRenderer.wrap(graphics, awaitingServer ? "Turning cable..." : live == 0 ? "Start with the tile next to SOLAR."
                        : progress == null ? "" : progress.feedback(),
                left + 14, top + panelHeight - 19, panelWidth - 28, 8, 1, MUTED);
    }

    private void drawTile(final GuiGraphicsExtractor graphics, final int x, final int y, final int size, final int index,
                          final boolean highlighted, final boolean complete) {
        final boolean live = (complete && (state.aux() & 511) == 0 || (state.aux() & 1 << index) != 0) && Math.abs(cableTargets[index] - cableAngles[index]) < 5;
        final Color unlit = new Color(74, 92, 116), cable = live ? ACCENT : unlit;
        final float half = size / 2F, thickness = Math.max(4, size / 7);

        NovaRenderer.slot(graphics, x, y, size, size, new Color(16, 40, 44), highlighted);
        for (int pad = 0; pad < 4; pad++)
            NovaRenderer.rect(graphics, x + 3 + pad % 2 * (size - 8), y + 3 + pad / 2 * (size - 8), 2, 2, new Color(58, 96, 92));

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + half, y + half);
        graphics.pose().rotate((float) Math.toRadians(cableAngles[index]));
        final int mask = MissionPreparation.powerConnections(index, 0);
        for (int direction = 0; direction < 4; direction++) {
            if ((mask & 1 << direction) == 0) continue;
            graphics.pose().pushMatrix();
            graphics.pose().rotate(direction * (float) Math.PI / 2);
            if (live)
                NovaRenderer.rect(graphics, -thickness / 2 - 2, -half + 1, thickness + 4, half, NovaRenderer.alpha(ACCENT, 46));

            NovaRenderer.rect(graphics, -thickness / 2, -half + 1, thickness, half, cable);
            NovaRenderer.rect(graphics, -thickness / 2, -half + 1, 1, half, live ? new Color(255, 240, 190) : new Color(110, 130, 156));
            NovaRenderer.rect(graphics, thickness / 2 - 1, -half + 1, 1, half, live ? new Color(190, 136, 40) : new Color(46, 58, 78));
            if (live && minecraft.options.screenEffectScale().get() > 0) {
                final float travel = (float) ((animationTime * 1.4 + index * 0.13) % 1);
                NovaRenderer.rect(graphics, -1, -travel * (half - 3) - 2, 2, 3, Color.WHITE);
            }
            graphics.pose().popMatrix();
        }

        NovaRenderer.rect(graphics, -thickness / 2 - 2, -thickness / 2 - 2, thickness + 4, thickness + 4, new Color(5, 7, 13));
        NovaRenderer.rect(graphics, -thickness / 2 - 1, -thickness / 2 - 1, thickness + 2, thickness + 2, cable);
        graphics.pose().popMatrix();
    }

    private void drawOxygen(final GuiGraphicsExtractor graphics, final float elapsed, final boolean motion,
                            final boolean complete, final boolean paused) {
        final int packed = state.puzzleState(), pressure = MissionPreparation.oxygenPressure(packed), flow = MissionPreparation.oxygenFlow(packed);
        final int gaugeWidth = (panelWidth - 44) / 2;
        pressureNeedle = motion ? pressureNeedle + (pressure - pressureNeedle) * Math.min(1, elapsed * 9) : pressure;
        flowNeedle = motion ? flowNeedle + (flow - flowNeedle) * Math.min(1, elapsed * 9) : flow;

        NovaRenderer.text(graphics, paused ? "Mission paused. Your puzzle progress is safe."
                : complete ? "Both gauges are steady in the green zone." : "Turn the valves until both gauges sit in the green zone.",
                left + 14, top + 34, 8, MUTED);
        gauge(graphics, "PRESSURE", pressure, pressureNeedle, MissionPreparation.OXYGEN_PRESSURE_MIN, MissionPreparation.OXYGEN_PRESSURE_MAX, left + 14, top + 47, gaugeWidth);
        gauge(graphics, "FLOW", flow, flowNeedle, MissionPreparation.OXYGEN_FLOW_MIN, MissionPreparation.OXYGEN_FLOW_MAX, left + 30 + gaugeWidth, top + 47, gaugeWidth);
        if (complete) {
            NovaRenderer.text(graphics, "THE CREW CAN BREATHE", left + 14, valveTop + 2, 10, SUCCESS);
            NovaRenderer.wrap(graphics, "Pressure and flow are stable, so clean air reaches every room." + (turns > 0 ? " Solved in " + turns + " turns." : ""),
                    left + 14, valveTop + 15, panelWidth - 28, 8, 2, TEXT);
            NovaRenderer.callout(graphics, left + 14, valveTop + 40, panelWidth - 28, Math.min(48, top + panelHeight - 40 - valveTop - 40), "SPACE FACT",
                    "The Space Station makes oxygen for its crew by splitting recycled water with electricity.", OXYGEN);
            return;
        }

        for (int valve = 0; valve < 3; valve++) {
            final float target = -135 + MissionPreparation.oxygenValve(packed, valve) * 67.5F;
            valveAngles[valve] = motion ? valveAngles[valve] + (target - valveAngles[valve]) * Math.min(1, elapsed * 14) : target;
            final int cardX = left + 14 + valve * (valveWidth + 6), level = MissionPreparation.oxygenValve(packed, valve);
            NovaRenderer.slot(graphics, cardX, valveTop, valveWidth, 72, SURFACE, selectedValve == valve);
            NovaRenderer.text(graphics, VALVE_NAMES[valve], cardX + 47, valveTop + 5, 10, selectedValve == valve ? ACCENT : TEXT);
            for (int pip = 0; pip < 4; pip++)
                NovaRenderer.rect(graphics, cardX + 47 + pip * 9, valveTop + 17, 7, 4, pip < level ? OXYGEN : new Color(8, 11, 19));

            NovaRenderer.text(graphics, VALVE_EFFECTS[valve][0], cardX + 47, valveTop + 25, 8, MUTED);
            NovaRenderer.text(graphics, VALVE_EFFECTS[valve][1], cardX + 47, valveTop + 35, 8, MUTED);
        }

        final float stable = Math.clamp((state.aux() >>> 16) / (float) MissionPreparation.OXYGEN_STABLE_TICKS, 0, 1);
        final int progressY = top + panelHeight - 48;
        final String hint = stable >= 1 ? "Stable! Start the recycler." : stable > 0 ? "Hold steady..."
                : pressure > MissionPreparation.OXYGEN_PRESSURE_MAX ? "Pressure too high: open the vent or close a valve."
                : pressure < MissionPreparation.OXYGEN_PRESSURE_MIN ? "Pressure too low: open the intake or pump."
                : flow > MissionPreparation.OXYGEN_FLOW_MAX ? "Flow too fast: swap some pump for intake."
                : "Flow too slow: swap some intake for pump.";
        NovaRenderer.text(graphics, hint, left + 14, progressY - 12, 8, stable >= 1 ? SUCCESS : stable > 0 ? OXYGEN : ACCENT);
        NovaRenderer.meter(graphics, left + 14, progressY, panelWidth - 28, 7, stable, SUCCESS);
        if (panelHeight >= 270)
            NovaRenderer.callout(graphics, left + 14, valveTop + 80, panelWidth - 28, 36, "WHY IT MATTERS",
                    "Every valve changes two readings at once. Engineers test one change at a time and watch what happens.", SUCCESS);
    }

    private void gauge(final GuiGraphicsExtractor graphics, final String name, final int value, final float needle,
                       final int minimum, final int maximum, final int x, final int y, final int width) {
        final boolean safe = value >= minimum && value <= maximum;
        final String reading = value + (safe ? "  OK" : value < minimum ? "  LOW" : "  HIGH");
        NovaRenderer.text(graphics, name, x, y, 8, MUTED);
        NovaRenderer.text(graphics, reading, x + width - NovaRenderer.width(reading, 8), y, 8, safe ? SUCCESS : ACCENT);
        NovaRenderer.slot(graphics, x, y + 11, width, 14, SURFACE, false);

        final float inner = width - 4;
        NovaRenderer.rect(graphics, x + 2 + inner * minimum / 100F, y + 13, inner * (maximum - minimum) / 100F, 10, NovaRenderer.alpha(SUCCESS, safe ? 120 : 64));
        for (int tick = 1; tick < 10; tick++)
            NovaRenderer.rect(graphics, x + 2 + inner * tick / 10F, y + 20, 1, 3, new Color(90, 104, 134));

        final float needleX = x + 2 + inner * Math.clamp(needle, 0, 100) / 100F;
        NovaRenderer.rect(graphics, needleX - 2, y + 12, 5, 12, new Color(5, 7, 13));
        NovaRenderer.rect(graphics, needleX - 1, y + 12, 3, 12, safe ? SUCCESS : ACCENT);
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
            drawTile(graphics, getX(), getY(), getWidth(), index, isHoveredOrFocused() || selectedCell == index, false);
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
            NovaRenderer.chamfer(graphics, getX(), getY(), 36, 36, 8, isHoveredOrFocused() ? ACCENT : new Color(5, 7, 13));
            NovaRenderer.chamfer(graphics, getX() + 1, getY() + 1, 34, 34, 8, new Color(52, 66, 96));
            NovaRenderer.chamfer(graphics, getX() + 4, getY() + 4, 28, 28, 7, new Color(20, 27, 42));
            for (int notch = 0; notch < 5; notch++) {
                final double angle = Math.toRadians(-135 + notch * 67.5 - 90);
                NovaRenderer.rect(graphics, centerX + (float) Math.cos(angle) * 15 - 1, centerY + (float) Math.sin(angle) * 15 - 1, 2, 2,
                        notch <= MissionPreparation.oxygenValve(state.puzzleState(), index) ? OXYGEN : new Color(110, 124, 154));
            }

            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY);
            graphics.pose().rotate((float) Math.toRadians(valveAngles[index]));
            NovaRenderer.rect(graphics, -2, -12, 4, 13, OXYGEN);
            NovaRenderer.rect(graphics, -2, -12, 1, 13, new Color(200, 248, 255));
            graphics.pose().popMatrix();
            NovaRenderer.rect(graphics, centerX - 3, centerY - 3, 6, 6, new Color(200, 248, 255));
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.literal(getMessage().getString() + ". Valve level " + MissionPreparation.oxygenValve(state.puzzleState(), index)
                    + " of four. Activate to open; shift-activate or right-click to close.");
        }
    }

    private static final class AdjustmentButton extends Button {
        private final String symbol;

        private AdjustmentButton(final int x, final int y, final int width, final String label, final String symbol, final Runnable action) {
            super(x, y, width, 17, Component.literal(label), _ -> action.run(), DEFAULT_NARRATION);
            this.symbol = symbol;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            NovaRenderer.button(graphics, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), false);
            NovaRenderer.centered(graphics, symbol, getX(), getY() - 1, getWidth(), getHeight(), active ? TEXT : MUTED);
        }
    }
}
