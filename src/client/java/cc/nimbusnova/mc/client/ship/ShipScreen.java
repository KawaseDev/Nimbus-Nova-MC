package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.learning.LearningClient;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaSounds;
import cc.nimbusnova.mc.client.ship.rooms.RoomClient;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import cc.nimbusnova.mc.ship.ShipActionPayload;
import cc.nimbusnova.mc.ship.ShipPayload;
import cc.nimbusnova.mc.ship.ShipTask;
import cc.nimbusnova.mc.ship.ShipHoldActions;
import cc.nimbusnova.mc.client.ship.controls.HoldTaskButton;
import cc.nimbusnova.mc.hub.HubActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import com.mojang.blaze3d.platform.InputConstants;

import java.awt.Color;
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
    private int commitActions;
    private boolean lastCommit;
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
        if (state.complete() && !this.state.complete() || !state.phase().equals(this.state.phase()) && state.phase().equals("SUCCESS"))
            NovaSounds.success();
        else if (state.revision() != this.state.revision() && !state.task().isEmpty()) {
            if (state.board() != this.state.board() || !state.stage().equals(this.state.stage()))
                NovaSounds.step(1.1F);
            else if (lastCommit)
                NovaSounds.bad();
        }

        lastCommit = false;
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
        lastCommit = (commitActions & 1 << action) != 0;
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
        lastCommit = true;
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
    public boolean keyPressed(final KeyEvent event) {
        if (tablet || ended() || pending || state.complete() || !state.incident().isEmpty() || state.phase().equals("PAUSED"))
            return super.keyPressed(event);

        final boolean docking = state.task().equals("PILOT") && state.stage().equals("DOCKING");
        if (!docking && !state.task().equals("COMMS")) return super.keyPressed(event);

        final int action = switch (event.key()) {
            case InputConstants.KEY_A -> docking ? 0 : 1;
            case InputConstants.KEY_D -> docking ? 1 : 0;
            case InputConstants.KEY_W -> 2;
            case InputConstants.KEY_S -> 3;
            case InputConstants.KEY_C -> docking ? 4 : -1;
            default -> -1;
        };
        if (action < 0) return super.keyPressed(event);

        sendTaskAction(action);
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (tablet || ended() || state.complete() || !state.task().equals("COMMS") || scrollY == 0 || !state.incident().isEmpty())
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        sendTaskAction(minecraft.hasShiftDown() ? scrollY > 0 ? 2 : 3 : scrollY > 0 ? 0 : 1);
        return true;
    }

    @Override
    protected void init() {
        holdButtons.clear();
        commitActions = 0;
        panelWidth = Math.min(590, width - 16);
        panelHeight = Math.min(330, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        addRenderableWidget(new MissionButton(left + panelWidth - 70, top + panelHeight - 29, 56, 20, "Close", this::onClose));
        addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 29, 68, 20,
                tablet ? "Station" : "Mission", () -> { tablet = !tablet; rebuildWidgets(); })).active = !state.task().isEmpty();
        if (state.incident().isEmpty())
            addRenderableWidget(new MissionButton(left + (tablet ? 254 : 88), top + panelHeight - (tablet ? 60 : 29), tablet ? 129 : 110, 20,
                    "Room control", RoomClient::open));
        if (!state.incident().isEmpty()) {
            final MoonMission.Incident incident = MoonMission.Incident.valueOf(state.incident());
            addRenderableWidget(new MissionButton(left + 88, top + panelHeight - 29, 81, 20, "NASA source",
                    () -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(incident.source))));
            addRenderableWidget(new MissionButton(left + 175, top + panelHeight - 29, 53, 20, lessonPage == 2 ? "Back" : "Next",
                    () -> { lessonPage = (lessonPage + 1) % 3; rebuildWidgets(); }));
            addRenderableWidget(new MissionButton(left + 234, top + panelHeight - 29, 91, 20, "Crew check", () -> LearningClient.open("ship")));
            if (state.operator()) {
                addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 60, 160, 21, "Check / confirm", () -> {
                    if (LearningClient.solved("ship", state.incident())) command("acknowledge", 0);
                    else LearningClient.open("ship");
                })).active = !pending;
                control("resume", "Resume mission", left + 180, top + panelHeight - 60, 130);
            }
            return;
        }

        if (tablet || ended()) {
            if (state.canChooseRole()) {
                final int buttonWidth = (panelWidth - 38) / 3;
                for (final MoonMission.Role role : MoonMission.Role.values()) {
                    final int index = role.ordinal();
                    addRenderableWidget(new MissionButton(left + 14 + index % 3 * (buttonWidth + 5), top + 80 + index / 3 * 25,
                            buttonWidth, 21, ShipClient.label(role.name()), () -> command("role", role.ordinal()))).active = !pending;
                }
                if (state.phase().equals("READY") && !state.role().isEmpty())
                    addRenderableWidget(new MissionButton(left + 14 + 2 * (buttonWidth + 5), top + 105, buttonWidth, 21,
                            "Report preflight check", () -> command("preflight", 0))).active = !pending;
            }
            if (state.operator()) {
                final String action = state.phase().equals("READY") ? "start" : state.phase().equals("PAUSED") ? "resume" : ended() ? "reset" : "pause";
                control(action, state.phase().equals("READY") ? state.ready() ? "Launch mission" : "Finish ship checks" : state.phase().equals("PAUSED") ? "Resume mission"
                        : ended() ? "Reset mission" : "Pause mission", left + 14, top + panelHeight - 60, 120);
                if (!ended())
                    control("abort", "Abort mission", left + 140, top + panelHeight - 60, 108);
            } else if (state.phase().equals("READY") && state.ready())
                addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 60, 180, 20, "Ask teacher to start", () -> {
                    if (ClientPlayNetworking.canSend(HubActionPayload.TYPE)) ClientPlayNetworking.send(new HubActionPayload("request_start", ""));
                }));
            addRenderableWidget(new MissionButton(left + 88, top + panelHeight - 29, 87, 20, "Leave ship", () -> command("leave", 0)));
            if (state.variant().equals("EXPLORATION") && !ended()) {
                if (onSurface())
                    addRenderableWidget(new MissionButton(left + 180, top + panelHeight - 29, 116, 20, "Return to ship", () -> command("join", 0)));
                else {
                    addRenderableWidget(new MissionButton(left + 180, top + panelHeight - 29, 70, 20, "Get suit", () -> command("suit", 0))).active = !pending;
                    if (state.stage().equals("SURFACE") && state.phase().equals("ACTIVE"))
                        addRenderableWidget(new MissionButton(left + 255, top + panelHeight - 29, 80, 20, "Go outside", () -> command("surface", 0))).active = !pending;
                }
            }
            return;
        }

        if (state.stage().equals("SURFACE") && state.task().equals("SCIENCE"))
            addRenderableWidget(new MissionButton(left + 14, top + 145, 140, 23, "NASA sample image",
                    () -> ConfirmLinkScreen.confirmLinkNow(this, URI.create("https://ares.jsc.nasa.gov/astromaterials3d/sample-details.htm?sample=12038-7"))));
        if (state.complete()) return;
        final int bodyWidth = panelWidth - 28;
        if (fuses()) {
            final int cardWidth = (bodyWidth - 15) / 4;
            for (int cartridge = 1; cartridge <= 4; cartridge++)
                addRenderableWidget(new FuseCard(left + 14 + (cartridge - 1) * (cardWidth + 5), top + 57, cardWidth, cartridge));
            for (int slot = 0; slot < 4; slot++)
                addRenderableWidget(new FuseSocket(left + 14 + slot * (cardWidth + 5), top + 111, cardWidth, slot));
        } else if (state.task().equals("ENGINE") && (state.stage().equals("ORBIT") || state.stage().equals("RETURN"))) {
            commitButton(left + 14, top + 145, bodyWidth, state.stage().equals("ORBIT") ? "Verify capture power" : "Verify return power", 0);
        } else if (state.task().equals("PILOT") && state.stage().equals("DOCKING")) {
            final int controlX = left + 136;
            taskButton(controlX + 34, top + 76, 32, "Up", 2);
            taskButton(controlX, top + 102, 32, "Left", 0);
            taskButton(controlX + 34, top + 102, 48, "Coast", 4);
            taskButton(controlX + 84, top + 102, 38, "Right", 1);
            taskButton(controlX + 34, top + 128, 48, "Down", 3);
            commitButton(controlX + 127, top + 128, Math.max(91, bodyWidth - 249), "Capture port", 5);
        } else if (state.task().equals("PILOT") && state.stage().equals("NAVIGATION")) {
            final int chartWidth = bodyWidth * 2 / 3, chartHeight = Math.min(109, panelHeight - 118);
            for (int star = 0; star < 6; star++)
                addRenderableWidget(new StarButton(left + 14 + (int) (ShipPuzzleGraphics.STAR_X[star] * chartWidth) - 7,
                        top + 58 + (int) (ShipPuzzleGraphics.STAR_Y[star] * chartHeight) - 7, star));
            commitButton(left + chartWidth + 24, top + 143, bodyWidth - chartWidth - 10, "Fix heading", 6);
        } else if (state.task().equals("PILOT") && state.stage().equals("ORBIT")) {
            final int w = (bodyWidth - 10) / 3;
            taskButton(left + 14, top + 116, w, "Insufficient", 0);
            taskButton(left + 19 + w, top + 116, w, "Planned", 1);
            taskButton(left + 24 + 2 * w, top + 116, w, "Excessive", 2);
            commitButton(left + 14, top + 145, bodyWidth, "Commit capture burn", 3);
        } else if (state.task().equals("PILOT") && state.stage().equals("DESCENT")) {
            final int w = (bodyWidth - 10) / 3;
            taskButton(left + 14, top + 96, w, "Check guidance", 0);
            taskButton(left + 19 + w, top + 96, w, "Primary site", 5);
            taskButton(left + 24 + 2 * w, top + 96, w, "Alternate site", 1);
            taskButton(left + 14, top + 122, w, "Brake lateral", 2);
            taskButton(left + 19 + w, top + 122, w, "Brake vertical", 3);
            commitButton(left + 24 + 2 * w, top + 122, w, "Confirm contact", 4);
            taskButton(left + 14, top + 148, bodyWidth, "Hold to cancel landing", 6);
        } else if (state.task().equals("PILOT") && state.stage().equals("SURFACE")) {
            commitButton(left + 14, top + 145, (bodyWidth - 5) / 2, "Launch lander", 5);
            commitButton(left + 19 + (bodyWidth - 5) / 2, top + 145, (bodyWidth - 5) / 2, "Confirm rendezvous", 6);
        } else if (state.task().equals("PILOT") && state.stage().equals("RETURN")) {
            final int w = (bodyWidth - 10) / 3;
            taskButton(left + 14, top + 116, w, "Shallow", 2);
            taskButton(left + 19 + w, top + 116, w, "Planned", 3);
            taskButton(left + 24 + 2 * w, top + 116, w, "Steep", 4);
            taskButton(left + 14, top + 145, 95, "Ignite engine", 0);
            commitButton(left + 115, top + 145, 95, "Cut off engine", 1);
        } else if (state.task().equals("COMMS")) {
            taskButton(left + 14, top + 145, 27, "F-", 1);
            taskButton(left + 43, top + 145, 27, "F+", 0);
            taskButton(left + 80, top + 145, 27, "G-", 3);
            taskButton(left + 109, top + 145, 27, "G+", 2);
            commitButton(left + bodyWidth - 73, top + 145, 87, "Transmit", 4);
        } else if (state.stage().equals("SURFACE") && state.task().equals("MEDICAL")) {
            commitButton(left + 14, top + 145, bodyWidth, "Check crew suits", 0);
        } else if (state.stage().equals("SURFACE") && state.task().equals("SCIENCE")) {
            // The sample is collected at the physical site rather than through this panel.
        } else if (filter()) {
            final String[] parts = { "Housing", "Fan", "Seal", "Cartridge" };
            final int partWidth = (bodyWidth - 15) / 4;
            for (int part = 0; part < 4; part++)
                commitButton(left + 14 + part * (partWidth + 5), top + 119, partWidth, parts[part], part);
            commitButton(left + 14, top + 145, (bodyWidth - 5) / 2, "Pressure test", 4);
            commitButton(left + 19 + (bodyWidth - 5) / 2, top + 145, (bodyWidth - 5) / 2, "Activate scrubber", 5);
        } else if (state.task().equals("ENGINE") && state.stage().equals("EMERGENCY") || state.task().equals("GALLEY") && state.stage().equals("RESTART")) {
            final String[] loads = state.task().equals("ENGINE") ? new String[] { "Life support", "Science", "Comfort heat" }
                    : new String[] { "Guidance", "Air loop", "Laboratory" };
            final int buttonWidth = (bodyWidth - 10) / 3;
            for (int load = 0; load < 3; load++)
                taskButton(left + 14 + load * (buttonWidth + 5), top + 116, buttonWidth,
                        loads[load] + ((state.board() & 1 << load) != 0 ? " ON" : " OFF"), load);
            commitButton(left + 14, top + 145, bodyWidth, state.task().equals("ENGINE") ? "Isolate failed engine" : "Commit heating budget", 3);
        } else if (state.task().equals("STORAGE") && state.stage().equals("PREPARATION")) {
            final int bayWidth = (bodyWidth - 12) / 3;
            for (int bay = 0; bay < 3; bay++)
                addRenderableWidget(new CargoBay(left + 14 + bay * (bayWidth + 6), top + 77, bayWidth, bay));
            commitButton(left + 14, top + 145, bodyWidth, "Secure balanced cargo", 3);
        } else if (state.task().equals("GALLEY")) {
            final int pouchWidth = (bodyWidth - 12) / 3;
            for (int pouch = 0; pouch < 3; pouch++) {
                taskButton(left + 14 + pouch * (pouchWidth + 6), top + 119, pouchWidth / 2 - 2, "+ Fill " + (pouch + 1), pouch);
                taskButton(left + 16 + pouch * (pouchWidth + 6) + pouchWidth / 2, top + 119, pouchWidth / 2 - 2, "Drain", pouch + 3);
            }
            commitButton(left + 14, top + 145, bodyWidth, "Seal crew ration pouches", 6);
        } else if (state.task().equals("PLANTS")) {
            final int bedWidth = (bodyWidth - 18) / 4;
            for (int bed = 0; bed < 4; bed++) {
                taskButton(left + 14 + bed * (bedWidth + 6), top + 119, bedWidth / 2 - 2, "+2 B" + (bed + 1), bed);
                taskButton(left + 16 + bed * (bedWidth + 6) + bedWidth / 2, top + 119, bedWidth / 2 - 2, "-1 B" + (bed + 1), bed + 4);
            }
            commitButton(left + 14, top + 145, bodyWidth, "Verify moisture balance", 8);
        } else if (state.task().equals("MEDICAL")) {
            final int leadWidth = (bodyWidth - 12) / 3;
            for (int lead = 0; lead < 3; lead++) {
                taskButton(left + 14 + lead * (leadWidth + 6), top + 119, leadWidth / 2 - 2, "Scan " + (lead + 1), lead);
                taskButton(left + 16 + lead * (leadWidth + 6) + leadWidth / 2, top + 119, leadWidth / 2 - 2, "Replace", lead + 3);
            }
            commitButton(left + 14, top + 145, bodyWidth, "Run instrument self-test", 6);
        } else if (state.task().equals("SCIENCE")) {
            final String[] classes = { "Ferrous", "Layered", "Porous" };
            final int classWidth = (bodyWidth - 12) / 3;
            for (int kind = 1; kind <= 3; kind++) {
                addRenderableWidget(new ClassificationCard(left + 14 + (kind - 1) * (classWidth + 6), top + 57,
                        classWidth, classes[kind - 1], kind));
            }
            for (int sample = 0; sample < 3; sample++)
                addRenderableWidget(new SampleCard(left + 14 + sample * (classWidth + 6), top + 85, classWidth, sample));
            commitButton(left + 14, top + 145, bodyWidth, "Archive verified specimens", 9);
        } else if (state.task().equals("MEETING"))
            taskButton(left + 14, top + 119, bodyWidth, state.phase().equals("READY") ? "Check intercom" : "Call crew meeting", 0);
    }

    private boolean fuses() {
        return state.task().equals("ENGINE") && (state.stage().equals("PREPARATION") || state.stage().equals("RESTART"));
    }

    private boolean filter() {
        return state.task().equals("MEDICAL") && state.stage().equals("FILTER");
    }

    private boolean onSurface() {
        return minecraft.level != null && minecraft.level.dimension().equals(LunarSurface.DIMENSION);
    }

    private boolean ended() {
        return state.phase().equals("SUCCESS") || state.phase().equals("PARTIAL") || state.phase().equals("FAILED") || state.phase().equals("ABORTED");
    }

    private void control(final String action, final String label, final int x, final int y, final int buttonWidth) {
        addRenderableWidget(new MissionButton(x, y, buttonWidth, 21, label, () -> command(action, 0))).active = !pending && (!action.equals("start") || state.ready());
    }

    private void commitButton(final int x, final int y, final int buttonWidth, final String label, final int action) {
        commitActions |= 1 << action;
        taskButton(x, y, buttonWidth, label, action);
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
        NovaRenderer.window(graphics, left, top, panelWidth, panelHeight, BACKGROUND);
        NovaRenderer.title(graphics, left, top, panelWidth,
                (state.variant().equals("EXPLORATION") ? "Moon exploration" : "Return Window") + " / " + (tablet ? "Crew tablet" : ShipClient.label(state.task())));
        final String stage = ShipClient.label(state.stage()).toUpperCase(), role = ShipClient.label(state.role().isEmpty() ? "No role" : state.role()).toUpperCase();
        NovaRenderer.chip(graphics, stage, left + 14, top + 35, ACCENT);
        NovaRenderer.chip(graphics, role, left + 18 + NovaRenderer.chipWidth(stage), top + 35, WARNING);
        NovaRenderer.text(graphics, ShipClient.telemetry(state), left + 24 + NovaRenderer.chipWidth(stage) + NovaRenderer.chipWidth(role), top + 37, 8, MUTED);
        if (!state.incident().isEmpty()) {
            final MoonMission.Incident incident = MoonMission.Incident.valueOf(state.incident());
            NovaRenderer.text(graphics, incident.title, left + 14, top + 56, 12, WARNING);
            NovaRenderer.wrap(graphics, lessonPage == 0 ? incident.observation + " " + incident.explanation
                            : lessonPage == 1 ? incident.hazard + " " + incident.question : incident.evidence,
                    left + 14, top + 78, panelWidth - 28, 10, Math.max(3, (panelHeight - 149) / 12), TEXT);
        } else if (tablet || ended()) {
            final String briefing = state.phase().equals("READY") ? switch (state.role()) {
                case "POWER" -> "Check power sources and the return reserve.";
                case "LIFE_SUPPORT" -> "Check oxygen, CO2 removal and cabin pressure.";
                case "SUPPLIES" -> "Check water and choose cargo for the return.";
                case "MAINTENANCE" -> "Check spare parts and the ship's systems.";
                case "PILOT" -> "Check the flight plan and maneuver fuel.";
                default -> "Choose a role and report your preflight check.";
            } : state.canChooseRole() ? "Choose a crew role to receive your objective." : state.title() + ". " + state.instruction();
            NovaRenderer.wrap(graphics, briefing, left + 14, top + 55, panelWidth - 28, 10,
                    state.phase().equals("READY") ? 2 : Math.max(3, (panelHeight - 129) / 12), TEXT);
        } else if (state.complete() && state.stage().equals("SURFACE") && state.task().equals("SCIENCE")) {
            NovaRenderer.wrap(graphics, "Compare the simulated basalt with NASA's Apollo 12 sample. What similarities and differences can you see? A Minecraft block is not a scientific identification.", left + 14, top + 65, panelWidth - 28, 10, 5, TEXT);
        } else if (state.complete()) {
            NovaRenderer.text(graphics, "STATION CHECK COMPLETE", left + 14, top + 58, 10, SUCCESS);
            NovaRenderer.text(graphics, "Close this panel and follow your next crew objective.", left + 14, top + 71, 8, TEXT);
            NovaRenderer.callout(graphics, left + 14, top + 88, panelWidth - 28, Math.min(50, panelHeight - 146), "SPACE FACT", fact(), WARNING);
        } else
            drawTask(graphics);

        if (state.incident().isEmpty())
            NovaRenderer.wrap(graphics, pending ? "Waiting for the crew server..." : state.feedback(), left + 14,
                    top + panelHeight - (tablet || ended() ? 85 : 52), panelWidth - 28, 8, 2, MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawTask(final GuiGraphicsExtractor graphics) {
        final int bodyWidth = panelWidth - 28;
        if (fuses()) {
            NovaRenderer.text(graphics, "Pick a fuse, then click the socket with the same number.", left + 14, top + 94, 8, MUTED);
        } else if (state.task().equals("ENGINE") && state.stage().equals("ORBIT")) {
            NovaRenderer.wrap(graphics, "Check that capture power is available while keeping at least 30 units for the return.", left + 14, top + 65, bodyWidth, 10, 4, MUTED);
        } else if (state.task().equals("ENGINE") && state.stage().equals("RETURN")) {
            NovaRenderer.wrap(graphics, "Check the remaining battery before the return burn and Earth entry. Discuss whether the crew has enough reserve.", left + 14, top + 65, bodyWidth, 10, 4, MUTED);
        } else if (state.task().equals("PILOT") && state.stage().equals("DOCKING")) {
            ShipPuzzleGraphics.docking(graphics, state, left + 14, top + 57, 106);
            final int velocityX = (state.board() >> 8 & 7) - 2, velocityY = (state.board() >> 11 & 7) - 2;
            final String drift = velocityX == 0 && velocityY == 0 ? "Drift: none" : "Drift: "
                    + (velocityX == 0 ? "" : (velocityX < 0 ? "left " : "right ") + Math.abs(velocityX) + " ")
                    + (velocityY == 0 ? "" : (velocityY < 0 ? "up " : "down ") + Math.abs(velocityY));
            NovaRenderer.text(graphics, drift, left + 136, top + 60, 8, velocityX == 0 && velocityY == 0 ? SUCCESS : WARNING);
            NovaRenderer.wrap(graphics, "Each push adds drift. Push the other way to brake. Keys: WASD, C to coast.", left + 267, top + 76, bodyWidth - 253, 8, 5, MUTED);
            NovaRenderer.text(graphics, "Stop inside the green box, then capture.", left + 136, top + 156, 8, MUTED);
        } else if (state.task().equals("PILOT") && state.stage().equals("NAVIGATION")) {
            final int chartWidth = bodyWidth * 2 / 3, chartHeight = Math.min(109, panelHeight - 118);
            ShipPuzzleGraphics.stars(graphics, state, left + 14, top + 58, chartWidth, chartHeight);
            ShipPuzzleGraphics.referenceStars(graphics, state.aux(), left + chartWidth + 24, top + 58, bodyWidth - chartWidth - 10, 53);
            NovaRenderer.text(graphics, "Pick the 3 sharp stars", left + 19, top + 46 + chartHeight, 8, MUTED);
        } else if (state.task().equals("PILOT") && state.stage().equals("ORBIT")) {
            ShipPuzzleGraphics.orbit(graphics, Math.clamp(state.board(), 0, 2), left + 14, top + 55, 150, 58);
            NovaRenderer.wrap(graphics, "Try each burn and watch the path. Too little flies past the Moon. Too much dives at the surface.", left + 172, top + 56, bodyWidth - 158, 8, 4, MUTED);
            NovaRenderer.text(graphics, "Orbital fuel " + state.aux(), left + 172, top + 104, 8, ACCENT);
        } else if (state.task().equals("PILOT") && state.stage().equals("DESCENT")) {
            ShipPuzzleGraphics.descent(graphics, state, left + 14, top + 55, bodyWidth);
        } else if (state.task().equals("PILOT") && state.stage().equals("SURFACE")) {
            NovaRenderer.wrap(graphics, (state.board() == 0 ? "After the crew and suit check, launch using the dedicated ascent reserve." : "Verify alignment and the mechanical lock before transferring to the command module.") + " Ascent reserve: " + state.aux() + ".", left + 14, top + 65, bodyWidth, 10, 4, MUTED);
        } else if (state.stage().equals("SURFACE") && (state.task().equals("MEDICAL") || state.task().equals("SCIENCE"))) {
            NovaRenderer.wrap(graphics, state.task().equals("MEDICAL") ? "Confirm suit communications and the surface reserve before an excursion." : "After the suit check, use the crew tablet to go outside and collect the sample.", left + 14, top + 65, bodyWidth, 10, 4, MUTED);
        } else if (state.task().equals("PILOT") && state.stage().equals("RETURN")) {
            ShipPuzzleGraphics.burn(graphics, state, left + 14, top + 57, bodyWidth, 55);
            NovaRenderer.text(graphics, "Burn " + (state.board() & 255) + " / cut off at 6-8", left + 219, top + 152, 8,
                    (state.board() & 256) != 0 ? WARNING : MUTED);
        } else if (state.task().equals("COMMS")) {
            ShipPuzzleGraphics.signal(graphics, state, left + 14, top + 69, bodyWidth, 58);
            NovaRenderer.text(graphics, "Match the dotted wave. A/D or scroll: frequency. W/S: gain.", left + 14, top + 57, 8, MUTED);
            NovaRenderer.text(graphics, "FREQ " + (state.board() & 15), left + 14, top + 133, 8, ACCENT);
            NovaRenderer.text(graphics, "GAIN " + (state.board() >> 4 & 15), left + 80, top + 133, 8, ACCENT);
            final int quality = state.aux() & 255;
            NovaRenderer.text(graphics, "LOCK " + quality + "%", left + 146, top + 133, 8, quality >= 90 ? SUCCESS : MUTED);
            NovaRenderer.meter(graphics, left + 146, top + 151, Math.max(24, bodyWidth - 232), 7, quality / 100.0,
                    (state.aux() & 256) != 0 ? WARNING : quality >= 90 ? SUCCESS : ACCENT);
        } else if (filter()) {
            ShipPuzzleGraphics.adapter(graphics, state, left + 14, top + 55, bodyWidth, 52);
            NovaRenderer.text(graphics, "Fit the parts in order 1 to 4. Pressure test " + state.aux() + "/3s.", left + 14, top + 109, 8, MUTED);
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
                case "SCIENCE" -> NovaRenderer.text(graphics, "Pick a rock type, then click the sample that matches.", left + 14, top + 135, 8, MUTED);
                default -> ShipActivityGraphics.intercom(graphics, state, left + 14, top + 55, bodyWidth);
            }
        }
    }

    private String fact() {
        return switch (state.task()) {
            case "ENGINE" -> state.stage().equals("PREPARATION") || state.stage().equals("RESTART")
                    ? "A fuse is a safety link. It breaks the circuit before too much current can overheat a wire."
                    : "Apollo 13's crew ran their ship on about a fifth of its normal power to save the batteries for the trip home.";
            case "PILOT" -> switch (state.stage()) {
                case "DOCKING" -> "In space nothing slows you down. A ship keeps drifting until a thruster pushes the other way.";
                case "NAVIGATION" -> "Apollo astronauts checked their position by sighting stars through a sextant, like sailors at sea.";
                case "ORBIT" -> "To orbit the Moon, a spacecraft fires its engine to slow down so the Moon's gravity can catch it.";
                case "DESCENT" -> "The Moon has no air, so parachutes do not work. Landers brake with rocket engines all the way down.";
                case "SURFACE" -> "Apollo's lunar module launched from the Moon to meet the command module waiting in orbit.";
                default -> "A returning capsule must hit a narrow path: too shallow and it skips off the air, too steep and it overheats.";
            };
            case "COMMS" -> "A radio signal takes about 1.3 seconds to travel from the Moon to Earth.";
            case "MEDICAL" -> state.stage().equals("FILTER")
                    ? "Apollo 13's crew built an air filter adapter from a bag, cardboard and tape so a square filter fit a round hole."
                    : "A noisy sensor can hide a real problem, so astronauts test their instruments before trusting a reading.";
            case "GALLEY" -> state.stage().equals("RESTART")
                    ? "Apollo 13's cabin cooled to about 3 degrees Celsius while the crew saved power for the trip home."
                    : "The Space Station recycles most of its water, so every drop is measured.";
            case "PLANTS" -> "Astronauts have grown lettuce, radishes and chili peppers on the Space Station.";
            case "SCIENCE" -> "Geologists sort rocks by what they observe. Holes, layers and magnetism show how a rock formed.";
            case "STORAGE" -> "Every extra kilogram needs extra fuel, so mission planners weigh and balance all cargo.";
            case "EXERCISE" -> "Space Station astronauts exercise about two hours a day to keep muscles and bones strong.";
            default -> "Astronauts and Mission Control talk through a problem together before anyone acts.";
        };
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
            NovaRenderer.button(graphics, getX(), getY(), width, height, true, isHoveredOrFocused(), selectedSampleClass == classification);
            NovaRenderer.centered(graphics, label, getX(), getY() - 1, width, height, selectedSampleClass == classification ? WARNING : TEXT);
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
            final float centerX = getX() + 7, centerY = getY() + 7;
            if ((state.aux() & 1 << star) != 0) {
                final Color glow = NovaRenderer.alpha(TEXT, 60 + Math.round(60 * NovaRenderer.pulse(3 + star)));
                NovaRenderer.rect(graphics, centerX - 5, centerY, 11, 1, glow);
                NovaRenderer.rect(graphics, centerX, centerY - 5, 1, 11, glow);
                NovaRenderer.rect(graphics, centerX - 2, centerY, 5, 1, TEXT);
                NovaRenderer.rect(graphics, centerX, centerY - 2, 1, 5, TEXT);
                NovaRenderer.rect(graphics, centerX - 1, centerY - 1, 3, 3, Color.WHITE);
            } else if (star == 0) {
                NovaRenderer.rect(graphics, centerX - 4, centerY - 4, 9, 9, NovaRenderer.alpha(MUTED, 50));
                NovaRenderer.rect(graphics, centerX - 2, centerY - 2, 5, 5, NovaRenderer.alpha(MUTED, 110));
            } else if (star == 2) {
                NovaRenderer.rect(graphics, centerX - 5, centerY - 1, 4, 4, MUTED);
                NovaRenderer.rect(graphics, centerX + 2, centerY - 2, 4, 4, MUTED);
            } else {
                NovaRenderer.rect(graphics, centerX - 6, centerY, 6, 1, NovaRenderer.alpha(MUTED, 110));
                NovaRenderer.rect(graphics, centerX, centerY - 1, 5, 3, MUTED);
            }

            if (selected)
                NovaRenderer.brackets(graphics, getX() - 3, getY() - 3, width + 6, height + 6, 4, ACCENT);
            if (isHoveredOrFocused())
                NovaRenderer.stroke(graphics, getX() - 1, getY() - 1, width + 2, height + 2, 1, WARNING);

            NovaRenderer.text(graphics, Character.toString((char) ('A' + star)), getX() + 14, getY() - 7, 8, selected ? ACCENT : MUTED);
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
            NovaRenderer.slot(graphics, getX(), getY(), width, height, SURFACE, selectedCartridge == cartridge || isHoveredOrFocused());
            fuse(graphics, getX() + 6, getY() + 8, cartridge);
            NovaRenderer.text(graphics, cartridge * 2 + 2 + "A", getX() + 40, getY() + 11, 8, selectedCartridge == cartridge ? WARNING : TEXT);
        }
    }

    private static void fuse(final GuiGraphicsExtractor graphics, final float x, final float y, final int cartridge) {
        final Color band = new Color[] { new Color(95, 225, 240), new Color(255, 200, 87), new Color(255, 140, 90), new Color(190, 140, 255) }[cartridge - 1];
        NovaRenderer.rect(graphics, x, y, 28, 13, new Color(5, 7, 13));
        NovaRenderer.rect(graphics, x + 1, y + 1, 5, 11, new Color(190, 198, 214));
        NovaRenderer.rect(graphics, x + 22, y + 1, 5, 11, new Color(190, 198, 214));
        NovaRenderer.rect(graphics, x + 6, y + 2, 16, 9, new Color(232, 238, 248, 70));
        NovaRenderer.rect(graphics, x + 6, y + 6, 16, 1, new Color(232, 238, 248));
        NovaRenderer.rect(graphics, x + 9, y + 2, 3 + cartridge * 2, 9, band);
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
            final boolean matched = installed == required;
            NovaRenderer.slot(graphics, getX(), getY(), width, height, SURFACE, isHoveredOrFocused());
            NovaRenderer.text(graphics, "BUS " + (slot + 1), getX() + 6, getY() + 5, 8, MUTED);
            final String need = "needs " + (required * 2 + 2) + "A";
            NovaRenderer.text(graphics, need, getX() + width - 6 - NovaRenderer.width(need, 8), getY() + 5, 8, matched ? SUCCESS : WARNING);
            NovaRenderer.slot(graphics, getX() + 5, getY() + 18, width - 10, 25, BACKGROUND, false);
            NovaRenderer.rect(graphics, getX() + 5, getY() + 18, 2, 25, matched ? SUCCESS : installed == 0 ? BORDER : WARNING);
            if (installed == 0) {
                NovaRenderer.text(graphics, "empty", getX() + 13, getY() + 27, 8, MUTED);
                return;
            }

            fuse(graphics, getX() + 12, getY() + 24, installed);
            NovaRenderer.text(graphics, matched ? "OK" : "wrong", getX() + 45, getY() + 27, 8, matched ? SUCCESS : WARNING);
        }
    }
}
