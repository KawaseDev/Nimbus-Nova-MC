package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.ship.ShipActionPayload;
import cc.nimbusnova.mc.ship.ShipPayload;
import cc.nimbusnova.mc.ship.ShipTask;
import cc.nimbusnova.mc.ship.ShipHoldActions;
import cc.nimbusnova.mc.client.ship.controls.HoldTaskButton;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class ShipScreen extends NovaScreen implements ShipPalette {
    private ShipPayload state;
    private boolean tablet, pending;
    private int lessonPage, left, top, panelWidth, panelHeight;
    private int selectedCartridge = 1;
    private int selectedSampleClass = 1;
    private final List<HoldTaskButton> holdButtons = new ArrayList<>();

    public ShipScreen(final ShipPayload state) {
        super(Component.literal("Return Window"));
        this.state = state;
        tablet = state.task().isEmpty();
    }

    public ShipPayload state() {
        return state;
    }

    public void update(final ShipPayload state) {
        final boolean changed = !this.state.station().equals(state.station()) || !this.state.stage().equals(state.stage())
                || !this.state.phase().equals(state.phase()) || this.state.board() != state.board() || state.complete();
        if (!changed && holdButtons.stream().anyMatch(HoldTaskButton::holding)) {
            this.state = state;
            return;
        }
        if (changed)
            holdButtons.forEach(HoldTaskButton::cancel);
        if (!this.state.station().equals(state.station()) || state.open())
            tablet = state.task().isEmpty();
        if (!this.state.incident().equals(state.incident()))
            lessonPage = 0;

        this.state = state;
        pending = false;
        rebuildWidgets();
    }

    public void sendTaskAction(final int action) {
        if (pending || state.complete() || state.task().isEmpty() || heldAction(action)) return;
        pending = true;
        ClientPlayNetworking.send(new ShipActionPayload("task", state.station(), state.revision(), action));
        rebuildWidgets();
    }

    public void beginHeldTaskAction(final int action) {
        if (pending || state.complete() || !heldAction(action)) return;
        ClientPlayNetworking.send(new ShipActionPayload("hold_begin", state.station(), state.revision(), action));
    }

    public void finishHeldTaskAction(final int action) {
        if (pending || state.complete() || !heldAction(action)) return;
        pending = true;
        ClientPlayNetworking.send(new ShipActionPayload("hold_finish", state.station(), state.revision(), action));
        rebuildWidgets();
    }

    private boolean heldAction(final int action) {
        return !state.task().isEmpty() && ShipHoldActions.requires(ShipTask.valueOf(state.task()), MoonMission.Stage.valueOf(state.stage()), action);
    }

    @Override
    public void removed() {
        holdButtons.forEach(HoldTaskButton::cancel);
        super.removed();
    }

    private void command(final String action, final int value) {
        if (pending) return;
        pending = true;
        ClientPlayNetworking.send(new ShipActionPayload(action, BlockPos.ZERO, state.revision(), value));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        holdButtons.clear();
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
        final int bodyWidth = panelWidth - 28;
        if (fuses()) {
            final int cardWidth = (bodyWidth - 15) / 4;
            for (int cartridge = 1; cartridge <= 4; cartridge++)
                addRenderableWidget(new FuseCard(left + 14 + (cartridge - 1) * (cardWidth + 5), top + 57, cardWidth, cartridge));
            for (int slot = 0; slot < 4; slot++)
                addRenderableWidget(new FuseSocket(left + 14 + slot * (cardWidth + 5), top + 111, cardWidth, slot));
        } else if (state.task().equals("PILOT") && state.stage().equals("DOCKING")) {
            final int controlX = left + 136;
            taskButton(controlX + 34, top + 76, 32, "Up", 2);
            taskButton(controlX, top + 102, 32, "Left", 0);
            taskButton(controlX + 34, top + 102, 48, "Coast", 4);
            taskButton(controlX + 84, top + 102, 38, "Right", 1);
            taskButton(controlX + 34, top + 128, 48, "Down", 3);
            taskButton(controlX + 127, top + 128, Math.max(91, bodyWidth - 249), "Capture port", 5);
        } else if (state.task().equals("PILOT") && state.stage().equals("NAVIGATION")) {
            final int chartWidth = bodyWidth * 2 / 3, chartHeight = Math.min(109, panelHeight - 118);
            for (int star = 0; star < 6; star++)
                addRenderableWidget(new StarButton(left + 14 + (int) (ShipPuzzleGraphics.STAR_X[star] * chartWidth) - 7,
                        top + 58 + (int) (ShipPuzzleGraphics.STAR_Y[star] * chartHeight) - 7, star));
            taskButton(left + chartWidth + 24, top + 143, bodyWidth - chartWidth - 10, "Fix heading", 6);
        } else if (state.task().equals("PILOT") && state.stage().equals("RETURN")) {
            taskButton(left + 14, top + 145, 95, "Ignite engine", 0);
            taskButton(left + 115, top + 145, 95, "Cut off engine", 1);
        } else if (state.task().equals("COMMS")) {
            taskButton(left + 69, top + 145, 27, "F-", 1);
            taskButton(left + 99, top + 145, 27, "F+", 0);
            taskButton(left + 183, top + 145, 27, "G-", 3);
            taskButton(left + 213, top + 145, 27, "G+", 2);
            taskButton(left + bodyWidth - 73, top + 145, 87, "Transmit", 4);
        } else if (filter()) {
            final String[] parts = { "Housing", "Fan", "Seal", "Cartridge" };
            final int partWidth = (bodyWidth - 15) / 4;
            for (int part = 0; part < 4; part++)
                taskButton(left + 14 + part * (partWidth + 5), top + 119, partWidth, parts[part], part);
            taskButton(left + 14, top + 145, (bodyWidth - 5) / 2, "Pressure test", 4);
            taskButton(left + 19 + (bodyWidth - 5) / 2, top + 145, (bodyWidth - 5) / 2, "Activate scrubber", 5);
        } else if (state.task().equals("ENGINE") && state.stage().equals("EMERGENCY") || state.task().equals("GALLEY") && state.stage().equals("RESTART")) {
            final String[] loads = state.task().equals("ENGINE") ? new String[] { "Life support", "Science", "Comfort heat" }
                    : new String[] { "Guidance", "Air loop", "Laboratory" };
            final int buttonWidth = (bodyWidth - 10) / 3;
            for (int load = 0; load < 3; load++)
                taskButton(left + 14 + load * (buttonWidth + 5), top + 116, buttonWidth,
                        loads[load] + ((state.board() & 1 << load) != 0 ? " ON" : " OFF"), load);
            taskButton(left + 14, top + 145, bodyWidth, state.task().equals("ENGINE") ? "Isolate failed engine" : "Commit heating budget", 3);
        } else if (state.task().equals("STORAGE") && state.stage().equals("PREPARATION")) {
            final int bayWidth = (bodyWidth - 12) / 3;
            for (int bay = 0; bay < 3; bay++)
                addRenderableWidget(new CargoBay(left + 14 + bay * (bayWidth + 6), top + 77, bayWidth, bay));
            taskButton(left + 14, top + 145, bodyWidth, "Secure balanced cargo", 3);
        } else if (state.task().equals("GALLEY")) {
            final int pouchWidth = (bodyWidth - 12) / 3;
            for (int pouch = 0; pouch < 3; pouch++) {
                taskButton(left + 14 + pouch * (pouchWidth + 6), top + 119, pouchWidth / 2 - 2, "+ Fill " + (pouch + 1), pouch);
                taskButton(left + 16 + pouch * (pouchWidth + 6) + pouchWidth / 2, top + 119, pouchWidth / 2 - 2, "Drain", pouch + 3);
            }
            taskButton(left + 14, top + 145, bodyWidth, "Seal crew ration pouches", 6);
        } else if (state.task().equals("PLANTS")) {
            final int bedWidth = (bodyWidth - 18) / 4;
            for (int bed = 0; bed < 4; bed++) {
                taskButton(left + 14 + bed * (bedWidth + 6), top + 119, bedWidth / 2 - 2, "+2 B" + (bed + 1), bed);
                taskButton(left + 16 + bed * (bedWidth + 6) + bedWidth / 2, top + 119, bedWidth / 2 - 2, "-1 B" + (bed + 1), bed + 4);
            }
            taskButton(left + 14, top + 145, bodyWidth, "Verify moisture balance", 8);
        } else if (state.task().equals("MEDICAL")) {
            final int leadWidth = (bodyWidth - 12) / 3;
            for (int lead = 0; lead < 3; lead++) {
                taskButton(left + 14 + lead * (leadWidth + 6), top + 119, leadWidth / 2 - 2, "Scan " + (lead + 1), lead);
                taskButton(left + 16 + lead * (leadWidth + 6) + leadWidth / 2, top + 119, leadWidth / 2 - 2, "Replace", lead + 3);
            }
            taskButton(left + 14, top + 145, bodyWidth, "Run instrument self-test", 6);
        } else if (state.task().equals("SCIENCE")) {
            final String[] classes = { "Ferrous", "Layered", "Porous" };
            final int classWidth = (bodyWidth - 12) / 3;
            for (int kind = 1; kind <= 3; kind++) {
                addRenderableWidget(new ClassificationCard(left + 14 + (kind - 1) * (classWidth + 6), top + 57,
                        classWidth, classes[kind - 1], kind));
            }
            for (int sample = 0; sample < 3; sample++)
                addRenderableWidget(new SampleCard(left + 14 + sample * (classWidth + 6), top + 85, classWidth, sample));
            taskButton(left + 14, top + 145, bodyWidth, "Archive verified specimens", 9);
        } else if (!state.task().equals("EXERCISE"))
            taskButton(left + 14, top + 119, bodyWidth, "Call crew meeting", 0);
    }

    private boolean fuses() {
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
        if (heldAction(action)) {
            final HoldTaskButton button = new HoldTaskButton(x, y, buttonWidth, 23, label,
                    ShipHoldActions.durationTicks(ShipTask.valueOf(state.task()), MoonMission.Stage.valueOf(state.stage()), action),
                    () -> beginHeldTaskAction(action), () -> finishHeldTaskAction(action),
                    () -> {
                        if (ClientPlayNetworking.canSend(ShipActionPayload.TYPE))
                            ClientPlayNetworking.send(new ShipActionPayload("hold_cancel", state.station(), state.revision(), action));
                    });
            holdButtons.add(button);
            addRenderableWidget(button).active = !pending && !state.phase().equals("PAUSED");
        } else
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
        } else
            drawTask(graphics);

        if (state.incident().isEmpty())
            NovaRenderer.wrap(graphics, pending ? "Waiting for the crew server..." : state.feedback(), left + 14,
                    top + panelHeight - (tablet || ended() ? 83 : 48), panelWidth - 28, 8, 1, MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawTask(final GuiGraphicsExtractor graphics) {
        final int bodyWidth = panelWidth - 28;
        if (fuses()) {
            NovaRenderer.text(graphics, "Choose a rated cartridge, then tap its matching bus socket.", left + 14, top + 94, 8, MUTED);
        } else if (state.task().equals("PILOT") && state.stage().equals("DOCKING")) {
            ShipPuzzleGraphics.docking(graphics, state, left + 14, top + 57, 106);
            final int velocityX = (state.board() >> 8 & 7) - 2, velocityY = (state.board() >> 11 & 7) - 2;
            NovaRenderer.text(graphics, "Relative drift " + velocityX + ", " + velocityY, left + 136, top + 58, 9, ACCENT);
            NovaRenderer.wrap(graphics, "Thrust changes drift. Coast to move. Brake with opposite thrust.", left + 267, top + 78, bodyWidth - 253, 8, 4, MUTED);
            NovaRenderer.text(graphics, "Capture inside the green box at zero drift.", left + 136, top + 156, 7, MUTED);
        } else if (state.task().equals("PILOT") && state.stage().equals("NAVIGATION")) {
            final int chartWidth = bodyWidth * 2 / 3, chartHeight = Math.min(109, panelHeight - 118);
            ShipPuzzleGraphics.stars(graphics, state, left + 14, top + 58, chartWidth, chartHeight);
            ShipPuzzleGraphics.referenceStars(graphics, state.aux(), left + chartWidth + 24, top + 58, bodyWidth - chartWidth - 10, 53);
            NovaRenderer.text(graphics, "Match the reference triangle", left + 20, top + 159, 7, MUTED);
        } else if (state.task().equals("PILOT") && state.stage().equals("RETURN")) {
            ShipPuzzleGraphics.burn(graphics, state, left + 14, top + 57, bodyWidth, 78);
            NovaRenderer.text(graphics, "Burn " + (state.board() & 255) + " / cut off at 6-8", left + 219, top + 151, 9,
                    (state.board() & 256) != 0 ? WARNING : MUTED);
        } else if (state.task().equals("COMMS")) {
            ShipPuzzleGraphics.signal(graphics, state, left + 14, top + 69, bodyWidth, 58);
            NovaRenderer.text(graphics, "Match the gray reference wave. Avoid flat clipped peaks.", left + 14, top + 55, 8, MUTED);
            NovaRenderer.text(graphics, "Frequency " + (state.board() & 15), left + 14, top + 133, 8, ACCENT);
            NovaRenderer.text(graphics, "Gain " + (state.board() >> 4 & 15), left + 145, top + 133, 8, ACCENT);
            NovaRenderer.text(graphics, "Quality " + (state.aux() & 255) + "%" + ((state.aux() & 256) != 0 ? " / CLIPPED" : ""), left + 14, top + 151, 8, MUTED);
        } else if (filter()) {
            ShipPuzzleGraphics.adapter(graphics, state, left + 14, top + 57, bodyWidth, 53);
            NovaRenderer.text(graphics, "Match connectors left to right. Pressure test " + state.aux() + "/3s.", left + 14, top + 108, 8, MUTED);
        } else if (state.task().equals("GALLEY") && state.stage().equals("RESTART")) {
            ShipPuzzleGraphics.heating(graphics, state, left + 14, top + 54, bodyWidth, 57);
            NovaRenderer.text(graphics, "Plan " + ((state.board() & 4) != 0 ? "20" : "8") + " energy / 3 water. Guidance and air loop are essential.", left + 14, top + 104, 8, MUTED);
        } else if (state.task().equals("ENGINE") && state.stage().equals("EMERGENCY")) {
            ShipPuzzleGraphics.heating(graphics, state, left + 14, top + 54, bodyWidth, 57);
            NovaRenderer.text(graphics, "Keep life support powered. Isolate science and comfort loads.", left + 14, top + 104, 8, MUTED);
        } else {
            switch (state.task()) {
                case "STORAGE" -> ShipActivityGraphics.cargo(graphics, state, left + 14, top + 55, bodyWidth);
                case "GALLEY" -> ShipActivityGraphics.rations(graphics, state, left + 14, top + 55, bodyWidth);
                case "PLANTS" -> ShipActivityGraphics.plants(graphics, state, left + 14, top + 55, bodyWidth);
                case "MEDICAL" -> ShipActivityGraphics.instruments(graphics, state, left + 14, top + 55, bodyWidth);
                case "SCIENCE" -> NovaRenderer.text(graphics, "Choose a class, then place it on each observed specimen.", left + 14, top + 135, 6, MUTED);
                default -> ShipActivityGraphics.intercom(graphics, state, left + 14, top + 55, bodyWidth);
            }
        }
    }

    private final class ClassificationCard extends Button {
        private final int classification;
        private final String label;

        private ClassificationCard(final int x, final int y, final int cardWidth, final String label, final int classification) {
            super(x, y, cardWidth, 22, Component.literal("Choose " + label), _ -> {
                selectedSampleClass = classification;
                rebuildWidgets();
            }, DEFAULT_NARRATION);
            this.classification = classification;
            this.label = label;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            NovaRenderer.rect(graphics, getX(), getY(), width, height, 4, SURFACE);
            NovaRenderer.stroke(graphics, getX(), getY(), width, height, 4, selectedSampleClass == classification ? 1.5F : 0.5F,
                    selectedSampleClass == classification ? ACCENT : BORDER);
            NovaRenderer.text(graphics, label, getX() + (width - NovaRenderer.width(label, 9)) / 2, getY() + 5, 9, TEXT);
        }
    }

    private final class CargoBay extends Button {
        private final int bay;

        private CargoBay(final int x, final int y, final int bayWidth, final int bay) {
            super(x, y, bayWidth, 48, Component.literal("Cycle cargo in bay " + (bay + 1)), _ -> sendTaskAction(bay), DEFAULT_NARRATION);
            this.bay = bay;
            active = !pending;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            ShipActivityGraphics.cargoBay(graphics, state.board() >> bay * 2 & 3, getX(), getY(), width, height, bay, isHoveredOrFocused());
        }
    }

    private final class SampleCard extends Button {
        private final int sample;

        private SampleCard(final int x, final int y, final int cardWidth, final int sample) {
            super(x, y, cardWidth, 48, Component.literal("Classify specimen " + (char) ('A' + sample)),
                    _ -> sendTaskAction(sample * 3 + selectedSampleClass - 1), DEFAULT_NARRATION);
            this.sample = sample;
            active = !pending;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            ShipActivityGraphics.specimen(graphics, state.board() >> sample * 2 & 3, getX(), getY(), width, height,
                    sample, selectedSampleClass, isHoveredOrFocused());
        }
    }

    private final class StarButton extends Button {
        private final int star;

        private StarButton(final int x, final int y, final int star) {
            super(x, y, 14, 14, Component.literal("Toggle navigation star " + (char) ('A' + star)), _ -> sendTaskAction(star), DEFAULT_NARRATION);
            this.star = star;
            active = !pending;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            final boolean selected = (state.board() & 1 << star) != 0;
            final boolean reliable = (state.aux() & 1 << star) != 0;
            if (reliable) {
                NovaRenderer.glow(graphics, getX() + 3, getY() + 3, 8, 8, 4, 1.5F, BORDER);
                NovaRenderer.rect(graphics, getX() + 5, getY() + 5, 4, 4, 1, TEXT);
                NovaRenderer.rect(graphics, getX() + 6.5F, getY() + 1, 1, 12, 0, TEXT);
                NovaRenderer.rect(graphics, getX() + 1, getY() + 6.5F, 12, 1, 0, TEXT);
            } else if (star == 0)
                NovaRenderer.glow(graphics, getX(), getY(), width, height, 7, 5, MUTED);
            else if (star == 2) {
                NovaRenderer.rect(graphics, getX() + 2, getY() + 5, 4, 4, 2, MUTED);
                NovaRenderer.rect(graphics, getX() + 8, getY() + 5, 4, 4, 2, MUTED);
            } else {
                NovaRenderer.rect(graphics, getX() + 1, getY() + 6, 5, 2, 1, BORDER);
                NovaRenderer.rect(graphics, getX() + 7, getY() + 5, 5, 4, 2, MUTED);
            }
            if (selected)
                NovaRenderer.stroke(graphics, getX() - 2, getY() - 2, width + 4, height + 4, 5, 1, ACCENT);
            if (isHoveredOrFocused())
                NovaRenderer.stroke(graphics, getX(), getY(), width, height, 7, 1, WARNING);
            NovaRenderer.text(graphics, Character.toString((char) ('A' + star)), getX() + 11, getY() - 2, 6, selected ? ACCENT : MUTED);
        }
    }

    private final class FuseCard extends Button {
        private final int cartridge;

        private FuseCard(final int x, final int y, final int cardWidth, final int cartridge) {
            super(x, y, cardWidth, 29, Component.literal("Choose " + (cartridge * 2 + 2) + " amp cartridge"), _ -> {
                selectedCartridge = cartridge;
                rebuildWidgets();
            }, DEFAULT_NARRATION);
            this.cartridge = cartridge;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            NovaRenderer.rect(graphics, getX(), getY(), width, height, 5, SURFACE);
            NovaRenderer.stroke(graphics, getX(), getY(), width, height, 5, selectedCartridge == cartridge ? 1.5F : 0.5F,
                    selectedCartridge == cartridge ? ACCENT : BORDER);
            final float fuseX = getX() + 7, fuseY = getY() + 7;
            NovaRenderer.rect(graphics, fuseX + 2, fuseY + 2, 24, 13, 2, BORDER);
            NovaRenderer.rect(graphics, fuseX, fuseY, 24, 13, 2, cartridge % 2 == 0 ? ACCENT : WARNING);
            NovaRenderer.rect(graphics, fuseX + 3, fuseY + 4, 18, 5, 1, SURFACE);
            NovaRenderer.text(graphics, cartridge * 2 + 2 + "A", getX() + 39, getY() + 8, 10, TEXT);
        }
    }

    private final class FuseSocket extends Button {
        private final int slot;

        private FuseSocket(final int x, final int y, final int socketWidth, final int slot) {
            super(x, y, socketWidth, 49, Component.literal("Install cartridge in bus " + (slot + 1)),
                    _ -> sendTaskAction(slot * 4 + selectedCartridge - 1), DEFAULT_NARRATION);
            this.slot = slot;
            active = !pending;
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
            final int installed = state.board() >> slot * 4 & 15, required = state.aux() >> slot * 4 & 15;
            NovaRenderer.rect(graphics, getX(), getY(), width, height, 5, SURFACE);
            NovaRenderer.stroke(graphics, getX() + 5, getY() + 18, width - 10, 24, 3, 1.5F, installed == required ? SUCCESS : BORDER);
            NovaRenderer.text(graphics, "BUS " + (slot + 1) + " / " + (required * 2 + 2) + "A", getX() + 6, getY() + 5, 7, MUTED);
            NovaRenderer.text(graphics, installed == 0 ? "Empty" : installed * 2 + 2 + "A", getX() + 12, getY() + 25, 10,
                    installed == required ? SUCCESS : WARNING);
            if (isHoveredOrFocused())
                NovaRenderer.stroke(graphics, getX(), getY(), width, height, 5, 1, ACCENT);
        }
    }
}
