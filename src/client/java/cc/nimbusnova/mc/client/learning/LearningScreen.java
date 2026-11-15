package cc.nimbusnova.mc.client.learning;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.client.ship.ShipPalette;
import cc.nimbusnova.mc.client.ship.ShipClient;
import cc.nimbusnova.mc.client.ship.ShipScreen;
import cc.nimbusnova.mc.learning.AppliedLearning;
import cc.nimbusnova.mc.learning.LearningActionPayload;
import cc.nimbusnova.mc.learning.LearningPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class LearningScreen extends NovaScreen implements ShipPalette {
    private final @Nullable Screen previous;
    private LearningPayload state;
    private boolean pending;
    private int left, top, panelWidth, panelHeight;

    public LearningScreen(final @Nullable Screen previous, final LearningPayload state) {
        super(Component.literal("Crew evidence check"));
        this.previous = previous;
        this.state = state;
    }

    public LearningPayload state() {
        return state;
    }

    public void update(final LearningPayload state) {
        this.state = state;
        pending = false;
        rebuildWidgets();
    }

    public void answer(final int choice) {
        if (pending || state.solved()) return;
        pending = true;
        ClientPlayNetworking.send(new LearningActionPayload(state.source(), state.revision(), choice));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(580, width - 16);
        panelHeight = Math.min(310, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        final AppliedLearning.Case situation = AppliedLearning.Case.valueOf(state.incident());
        final int optionY = top + 98;
        if (!state.solved()) {
            for (int index = 0; index < situation.choices.size(); index++) {
                final int choice = index;
                addRenderableWidget(new MissionButton(left + 14, optionY + index * 23, panelWidth - 28, 21,
                        situation.choices.get(index), () -> answer(choice))).active = !pending;
            }
        }
        addRenderableWidget(new MissionButton(left + panelWidth - 134, top + panelHeight - 28, 120, 20,
                state.solved() ? "Back to crew" : "Discuss first", this::onClose));
    }

    @Override
    public void onClose() {
        final var current = ShipClient.state();
        if (previous instanceof ShipScreen ship && current != null)
            ship.update(current);
        minecraft.setScreenAndShow(previous);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        final AppliedLearning.Case situation = AppliedLearning.Case.valueOf(state.incident());
        NovaRenderer.rect(graphics, left, top, panelWidth, panelHeight, 9, BACKGROUND);
        NovaRenderer.text(graphics, "Crew check / " + situation.title, left + 14, top + 12, 13, ACCENT);
        final int meterWidth = (panelWidth - 38) / 2;
        meter(graphics, left + 14, top + 34, meterWidth, state.firstLabel(), state.firstValue(), Math.max(100, Math.max(state.firstValue(), state.secondValue())));
        meter(graphics, left + 24 + meterWidth, top + 34, meterWidth, state.secondLabel(), state.secondValue(), Math.max(100, Math.max(state.firstValue(), state.secondValue())));
        NovaRenderer.wrap(graphics, situation.observation, left + 14, top + 56, panelWidth - 28, 8, 2, MUTED);
        if (!state.solved())
            NovaRenderer.wrap(graphics, situation.prompt, left + 14, top + 77, panelWidth - 28, 8.5F, 2, TEXT);
        else {
            NovaRenderer.text(graphics, "Supported prediction", left + 14, top + 101, 16, SUCCESS);
            NovaRenderer.wrap(graphics, state.feedback(), left + 14, top + 125, panelWidth - 28, 10, 4, TEXT);
            NovaRenderer.wrap(graphics, "Apply this decision at the marked equipment after Mission Control resumes.", left + 14,
                    top + panelHeight - 60, panelWidth - 28, 8.5F, 2, MUTED);
        }
        if (!state.solved())
            NovaRenderer.wrap(graphics, pending ? "Checking with the crew server..." : state.feedback(), left + 14,
                    top + panelHeight - 55, panelWidth - 28, 7.5F, 2, state.feedback().startsWith("That action") ? WARNING : MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private static void meter(final GuiGraphicsExtractor graphics, final int x, final int y, final int width,
                              final String label, final int value, final int maximum) {
        NovaRenderer.text(graphics, label + (value >= 0 ? " " + value : ""), x, y, 8, TEXT);
        NovaRenderer.rect(graphics, x, y + 12, width, 6, 3, SURFACE);
        if (value > 0)
            NovaRenderer.rect(graphics, x, y + 12, width * Math.clamp(value, 0, maximum) / (float) maximum, 6, 3, ACCENT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
