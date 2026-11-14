package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.mission.MissionPreparation;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.ship.ShipActionPayload;
import cc.nimbusnova.mc.ship.ShipPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.net.URI;

@NullMarked
public final class ShipScreen extends NovaScreen implements ShipPalette {
    private ShipPayload state;
    private boolean tablet, pending;
    private int lessonPage, left, top, panelWidth, panelHeight;

    public ShipScreen(final ShipPayload state) {
        super(Component.literal("Return Window"));
        this.state = state;
        tablet = state.task().isEmpty();
    }

    public ShipPayload state() {
        return state;
    }

    public void update(final ShipPayload state) {
        if (!this.state.station().equals(state.station()) || state.open())
            tablet = state.task().isEmpty();
        if (!this.state.incident().equals(state.incident()))
            lessonPage = 0;

        this.state = state;
        pending = false;
        rebuildWidgets();
    }

    public void sendTaskAction(final int action) {
        if (pending || state.complete() || state.task().isEmpty()) return;
        pending = true;
        ClientPlayNetworking.send(new ShipActionPayload("task", state.station(), state.revision(), action));
        rebuildWidgets();
    }

    private void command(final String action, final int value) {
        if (pending) return;
        pending = true;
        ClientPlayNetworking.send(new ShipActionPayload(action, BlockPos.ZERO, state.revision(), value));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(590, width - 16);
        panelHeight = Math.min(330, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        addRenderableWidget(new MissionButton(left + panelWidth - 70, top + panelHeight - 29, 56, 20, "Close", this::onClose));
        addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 29, 68, 20,
                tablet ? "Station" : "Mission", () -> { tablet = !tablet; rebuildWidgets(); })).active = !state.task().isEmpty();
        if (!state.incident().isEmpty()) {
            final MoonMission.Incident incident = MoonMission.Incident.valueOf(state.incident());
            addRenderableWidget(new MissionButton(left + 88, top + panelHeight - 29, 81, 20, "NASA source",
                    () -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(incident.source))));
            addRenderableWidget(new MissionButton(left + 175, top + panelHeight - 29, 53, 20, lessonPage == 0 ? "Next" : "Back",
                    () -> { lessonPage = 1 - lessonPage; rebuildWidgets(); }));
            if (state.operator()) {
                control("acknowledge", "Confirm understanding", left + 14, top + panelHeight - 60, 160);
                control("resume", "Resume mission", left + 180, top + panelHeight - 60, 130);
            }
            return;
        }

        if (tablet || ended()) {
            if (state.role().isEmpty() && state.phase().equals("READY")) {
                final int buttonWidth = (panelWidth - 38) / 3;
                for (final MoonMission.Role role : MoonMission.Role.values()) {
                    final int index = role.ordinal();
                    addRenderableWidget(new MissionButton(left + 14 + index % 3 * (buttonWidth + 5), top + 80 + index / 3 * 25,
                            buttonWidth, 21, ShipClient.label(role.name()), () -> command("role", role.ordinal()))).active = !pending;
                }
            }
            if (state.operator()) {
                final String action = state.phase().equals("READY") ? "start" : state.phase().equals("PAUSED") ? "resume" : ended() ? "reset" : "pause";
                control(action, state.phase().equals("READY") ? "Launch mission" : state.phase().equals("PAUSED") ? "Resume mission"
                        : ended() ? "Reset mission" : "Pause mission", left + 14, top + panelHeight - 60, 120);
                if (!ended())
                    control("abort", "Abort mission", left + 140, top + panelHeight - 60, 108);
            }
            addRenderableWidget(new MissionButton(left + 88, top + panelHeight - 29, 87, 20, "Leave ship", () -> command("leave", 0)));
            return;
        }

        if (state.complete()) return;
        if (circuit()) {
            final int cellSize = Math.min(52, (panelHeight - 130) / 3);
            for (int cell = 0; cell < 9; cell++)
                addRenderableWidget(new CircuitTile(left + 18 + cell % 3 * (cellSize + 3), top + 64 + cell / 3 * (cellSize + 3), cellSize, cell));
        } else if (state.task().equals("PILOT") || state.task().equals("COMMS") || filter()) {
            final int spacing = (panelWidth - 28) / 3;
            for (int axis = 0; axis < 3; axis++) {
                final int centerX = left + 14 + spacing * axis + spacing / 2;
                addRenderableWidget(new DialButton(centerX - 31, top + 116, "Decrease " + dialName(axis), "-", axis * 2 + 1));
                addRenderableWidget(new DialButton(centerX + 4, top + 116, "Increase " + dialName(axis), "+", axis * 2));
            }
            taskButton(left + 14, top + 150, panelWidth - 28, filter() ? "Activate CO2 filter" : "Validate alignment", 6);
        } else if (state.task().equals("ENGINE") && state.stage().equals("EMERGENCY") || state.task().equals("GALLEY") && state.stage().equals("RESTART")) {
            final String[] loads = state.task().equals("ENGINE") ? new String[] { "Essential power", "Science load", "Heating load" }
                    : new String[] { "Guidance heat", "Filter heat", "Laboratory heat" };
            final int buttonWidth = (panelWidth - 38) / 3;
            for (int load = 0; load < 3; load++)
                taskButton(left + 14 + load * (buttonWidth + 5), top + 91, buttonWidth,
                        loads[load] + ((state.board() & 1 << load) != 0 ? " ON" : " OFF"), load);
            taskButton(left + 14, top + 131, panelWidth - 28, state.task().equals("ENGINE") ? "Isolate failed engine" : "Commit heating budget", 3);
        } else if (state.task().equals("STORAGE") && state.stage().equals("PREPARATION")) {
            taskButton(left + 14, top + 90, panelWidth - 28, "Load repair spares", 0);
            taskButton(left + 14, top + 122, panelWidth - 28, "Load extra science cargo", 1);
        } else
            taskButton(left + 14, top + 100, panelWidth - 28, switch (state.task()) {
                case "SCIENCE" -> "Run sample experiment";
                case "MEDICAL" -> "Complete crew health check";
                case "GALLEY" -> "Allocate crew water";
                case "MEETING" -> "Send crew situation report";
                case "PLANTS" -> "Irrigate plant tray";
                case "EXERCISE" -> "Complete crew exercise";
                default -> "Use station";
            }, 0);
    }

    private String dialName(final int axis) {
        return filter() ? new String[] { "Intake", "Pump", "Vent" }[axis]
                : state.task().equals("COMMS") ? new String[] { "Bearing", "Frequency", "Gain" }[axis]
                : new String[] { "Yaw", "Pitch", "Burn timing" }[axis];
    }

    private boolean circuit() {
        return state.task().equals("ENGINE") && (state.stage().equals("PREPARATION") || state.stage().equals("RESTART"));
    }

    private boolean filter() {
        return state.task().equals("MEDICAL") && state.stage().equals("FILTER");
    }

    private boolean ended() {
        return state.phase().equals("SUCCESS") || state.phase().equals("PARTIAL") || state.phase().equals("FAILED") || state.phase().equals("ABORTED");
    }

    private void control(final String action, final String label, final int x, final int y, final int buttonWidth) {
        addRenderableWidget(new MissionButton(x, y, buttonWidth, 21, label, () -> command(action, 0))).active = !pending;
    }

    private void taskButton(final int x, final int y, final int buttonWidth, final String label, final int action) {
        addRenderableWidget(new MissionButton(x, y, buttonWidth, 23, label, () -> sendTaskAction(action))).active = !pending && !state.phase().equals("PAUSED");
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        NovaRenderer.rect(graphics, left, top, panelWidth, panelHeight, 10, BACKGROUND);
        NovaRenderer.text(graphics, "Return Window / " + (tablet ? "Crew tablet" : ShipClient.label(state.task())), left + 14, top + 12, 14, TEXT);
        NovaRenderer.text(graphics, ShipClient.label(state.stage()) + " / " + ShipClient.label(state.role().isEmpty() ? "No role assigned" : state.role())
                + " / " + ShipClient.telemetry(state), left + 14, top + 33, 8, ACCENT);
        if (!state.incident().isEmpty()) {
            final MoonMission.Incident incident = MoonMission.Incident.valueOf(state.incident());
            NovaRenderer.text(graphics, incident.title, left + 14, top + 56, 12, WARNING);
            NovaRenderer.wrap(graphics, lessonPage == 0 ? incident.observation + " " + incident.explanation
                            : incident.hazard + " " + incident.question,
                    left + 14, top + 78, panelWidth - 28, 10, Math.max(3, (panelHeight - 149) / 12), TEXT);
        } else if (tablet || ended()) {
            NovaRenderer.wrap(graphics, state.title() + ". " + state.instruction(), left + 14, top + 55, panelWidth - 28, 10,
                    state.role().isEmpty() && state.phase().equals("READY") ? 2 : Math.max(3, (panelHeight - 129) / 12), TEXT);
        } else if (state.complete()) {
            NovaRenderer.text(graphics, "Station check complete", left + 14, top + 73, 18, SUCCESS);
            NovaRenderer.wrap(graphics, "Close this panel and follow your next crew objective.", left + 14, top + 108, panelWidth - 28, 10, 3, TEXT);
        } else if (circuit()) {
            final int cellSize = Math.min(52, (panelHeight - 130) / 3), infoX = left + 27 + 3 * (cellSize + 3);
            final int infoWidth = panelWidth - (infoX - left) - 14;
            NovaRenderer.wrap(graphics, "Rotate the cable tiles. Connect the left supply to the right output.", infoX, top + 64, infoWidth, 10, 4, TEXT);
            NovaRenderer.wrap(graphics, "Bright lines carry power. The repaired path automatically restores the circuit.", infoX, top + 116, infoWidth, 9, 4, MUTED);
        } else if (state.task().equals("PILOT") || state.task().equals("COMMS") || filter()) {
            final int spacing = (panelWidth - 28) / 3;
            for (int axis = 0; axis < 3; axis++) {
                final int centerX = left + 14 + spacing * axis + spacing / 2;
                final String label = dialName(axis) + ": " + (state.board() >> axis * 3 & 7);
                NovaRenderer.text(graphics, label, centerX - NovaRenderer.width(label, 10) / 2, top + 70, 10, TEXT);
                final String target = "Target " + (filter() ? new int[] { 2, 2, 0 }[axis] : state.aux() >> axis * 3 & 7);
                NovaRenderer.text(graphics, target, centerX - NovaRenderer.width(target, 9) / 2, top + 90, 9, ACCENT);
            }
            if (filter()) {
                final String status = "Pressure " + (state.aux() & 255) + " / Flow " + (state.aux() >> 8 & 255)
                        + " / Stable " + (state.aux() >>> 16) + "/3s";
                NovaRenderer.text(graphics, status, left + 14, top + 54, 8, MUTED);
            }
        } else
            NovaRenderer.wrap(graphics, state.instruction(), left + 14, top + 54, panelWidth - 28, 9, 2, TEXT);

        if (state.incident().isEmpty())
            NovaRenderer.wrap(graphics, pending ? "Waiting for the crew server..." : state.feedback(), left + 14,
                    top + panelHeight - (tablet || ended() ? 83 : 48), panelWidth - 28, 8, 1, MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class DialButton extends Button {
        private final String symbol;

        private DialButton(final int x, final int y, final String label, final String symbol, final int action) {
            super(x, y, 27, 23, Component.literal(label), _ -> sendTaskAction(action), DEFAULT_NARRATION);
            this.symbol = symbol;
            active = !pending && !state.phase().equals("PAUSED");
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            NovaRenderer.rect(graphics, getX(), getY(), width, height, 5, isHoveredOrFocused() ? BORDER : SURFACE);
            NovaRenderer.text(graphics, symbol, getX() + (width - NovaRenderer.width(symbol, 13)) / 2, getY() + 3, 13, active ? TEXT : MUTED);
        }
    }

    private final class CircuitTile extends Button {
        private final int cell;

        private CircuitTile(final int x, final int y, final int size, final int cell) {
            super(x, y, size, size, Component.literal("Rotate ship tile " + (cell + 1)), _ -> sendTaskAction(cell), DEFAULT_NARRATION);
            this.cell = cell;
            active = !pending && !state.phase().equals("PAUSED");
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            final boolean powered = (MissionPreparation.energizedCells(state.board()) & 1 << cell) != 0;
            final float centerX = getX() + width / 2F, centerY = getY() + height / 2F;
            NovaRenderer.rect(graphics, getX(), getY(), width, height, 5, SURFACE);
            NovaRenderer.stroke(graphics, getX(), getY(), width, height, 5, 0.8F, isHoveredOrFocused() ? ACCENT : BORDER);
            final int ports = MissionPreparation.powerConnections(cell, state.board());
            if ((ports & 1) != 0)
                NovaRenderer.rect(graphics, centerX - 1.5F, getY(), 3, height / 2F, 1, powered ? ACCENT : MUTED);
            if ((ports & 2) != 0)
                NovaRenderer.rect(graphics, centerX, centerY - 1.5F, width / 2F, 3, 1, powered ? ACCENT : MUTED);
            if ((ports & 4) != 0)
                NovaRenderer.rect(graphics, centerX - 1.5F, centerY, 3, height / 2F, 1, powered ? ACCENT : MUTED);
            if ((ports & 8) != 0)
                NovaRenderer.rect(graphics, getX(), centerY - 1.5F, width / 2F, 3, 1, powered ? ACCENT : MUTED);
            NovaRenderer.rect(graphics, centerX - 3, centerY - 3, 6, 6, 3, powered ? ACCENT : TEXT);
        }
    }
}
