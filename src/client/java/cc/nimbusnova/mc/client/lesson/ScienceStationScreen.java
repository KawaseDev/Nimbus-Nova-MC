package cc.nimbusnova.mc.client.lesson;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.lesson.LessonActionPayload;
import cc.nimbusnova.mc.lesson.LessonModel;
import cc.nimbusnova.mc.lesson.LessonPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.awt.Color;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ScienceStationScreen extends NovaScreen {
    private LessonPayload state;
    private int left, top, panelWidth, panelHeight;
    private boolean pending;

    public ScienceStationScreen(final LessonPayload state) {
        super(Component.literal("Outpost science lab"));
        this.state = state;
    }

    public BlockPos position() {
        return state.position();
    }

    public LessonPayload state() {
        return state;
    }

    public void update(final LessonPayload state) {
        this.state = state;
        pending = false;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(490, width - 16);
        panelHeight = Math.min(270, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        final int controlsY = top + panelHeight - 78;
        final int gap = 5, count = state.kind() == 0 ? 4 : state.kind() == 2 ? 3 : 1;
        final int buttonWidth = (panelWidth - 28 - gap * (count - 1)) / count;
        for (int index = 0; index < count; index++) {
            final int action = index;
            final String label = state.kind() == 0 ? new String[] { "Seal", "Vent -20", "Pump +20", "Cycle" }[index]
                    : state.kind() == 1 ? "Transfer one +10 reserve cell" : "Layer " + (index + 1) + ": " + material(state.layers() >> index * 2 & 3);
            final MissionButton button = addRenderableWidget(new MissionButton(left + 14 + index * (buttonWidth + gap),
                    controlsY, buttonWidth, 24, label, () -> sendAction(action)));
            button.active = !pending;
        }
        addRenderableWidget(new MissionButton(left + panelWidth - 70, top + panelHeight - 30, 56, 20, "Close", this::onClose));
    }

    public void sendAction(final int action) {
        if (pending) return;
        pending = true;
        ClientPlayNetworking.send(new LessonActionPayload(state.position(), state.revision(), action));
        rebuildWidgets();
    }

    private static String material(final int material) {
        return switch (material) { case 1 -> "Glass"; case 2 -> "Water"; case 3 -> "Regolith"; default -> "Empty"; };
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        final Color background = new Color(13, 23, 32, 245), muted = new Color(159, 180, 191),
                accent = new Color(110, 220, 225), white = new Color(233, 243, 244);
        NovaRenderer.window(graphics, left, top, panelWidth, panelHeight, background);
        final String title = switch (state.kind()) { case 0 -> "Airlock pressure lab"; case 1 -> "Backup power reserve"; default -> "Shielding experiment"; };
        NovaRenderer.title(graphics, left, top, panelWidth, title);
        NovaRenderer.text(graphics, state.kind() == 0 ? "Training model / separate from walk-through base doors"
                : state.kind() == 1 ? "Finite shared storage / a choice for your crew"
                : "Authored comparison model / not a real radiation dose", left + 14, top + 35, 9, muted);
        if (state.kind() == 0) {
            NovaRenderer.text(graphics, "Mars: 0     Chamber: " + state.pressure() + "     Cabin: 100", left + 14, top + 58, 11, accent);
            NovaRenderer.meter(graphics, left + 14, top + 78, panelWidth - 28, 12, state.pressure() / 100D, accent);
            NovaRenderer.text(graphics, state.isolated() ? "SEALED / target " + state.target() + " model units, then cycle" : "1 Seal   2 Equalize   3 Cycle", left + 14, top + 101, 10, white);
        } else if (state.kind() == 1) {
            NovaRenderer.text(graphics, "Shared battery: " + state.battery() + " / 100", left + 14, top + 61, 14, accent);
            NovaRenderer.text(graphics, "Reserve cells left: " + state.cells() + " / 2", left + 14, top + 84, 12, white);
            NovaRenderer.wrap(graphics, "Use a cell now, or keep it for a storm? Full battery rejects a transfer.", left + 14, top + 106, panelWidth - 28, 9, 2, muted);
        } else {
            NovaRenderer.text(graphics, "Detector index: " + LessonModel.detectorIndex(state.layers()) + " / 100", left + 14, top + 61, 14, accent);
            NovaRenderer.text(graphics, "Carried mass: " + LessonModel.mass(state.layers()) + " model units", left + 14, top + 84, 12, white);
            NovaRenderer.wrap(graphics, "Tap layers to compare. More shielding also means more mass to transport.", left + 14, top + 106, panelWidth - 28, 9, 2, muted);
        }
        NovaRenderer.wrap(graphics, pending ? "Waiting for the server..." : state.feedback(), left + 14,
                top + panelHeight - 47, panelWidth - 98, 9, 2, muted);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
