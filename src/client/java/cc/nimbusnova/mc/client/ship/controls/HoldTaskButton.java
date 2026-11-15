package cc.nimbusnova.mc.client.ship.controls;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.ship.ShipPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;
import com.mojang.blaze3d.platform.InputConstants;

@NullMarked
public final class HoldTaskButton extends Button implements ShipPalette {
    private final Runnable beginAction, finishAction, cancelAction;
    private final long durationNanos;
    private boolean holding, mouseGesture;
    private long started;
    private int heldKey;

    public HoldTaskButton(final int x, final int y, final int width, final int height, final String label,
                          final int durationTicks, final Runnable begin, final Runnable finish, final Runnable cancel) {
        super(x, y, width, height, Component.literal(label), _ -> { /* w */ }, DEFAULT_NARRATION);
        beginAction = begin;
        finishAction = finish;
        cancelAction = cancel;
        // Allow a few network ticks before asking the server to approve completion.
        durationNanos = (durationTicks + 4L) * 50_000_000;
    }

    public boolean holding() {
        return holding;
    }

    public void cancel() {
        if (!holding) return;
        holding = false;
        cancelAction.run();
    }

    @Override
    public void onPress(final InputWithModifiers input) {
        if (!active || holding) return;
        mouseGesture = input instanceof MouseButtonEvent;
        heldKey = input instanceof KeyEvent event ? event.key() : InputConstants.KEY_SPACE;
        holding = true;
        started = System.nanoTime();
        beginAction.run();
    }

    @Override
    public void onRelease(final MouseButtonEvent event) {
        if (mouseGesture)
            cancel();
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        if (!active || !isFocused() || event.key() != InputConstants.KEY_SPACE && event.key() != InputConstants.KEY_RETURN
                && event.key() != InputConstants.KEY_NUMPADENTER) return false;
        onPress(event);
        return true;
    }

    @Override
    public boolean keyReleased(final KeyEvent event) {
        if (!holding || mouseGesture || event.key() != heldKey) return false;
        cancel();
        return true;
    }

    @Override
    public void setFocused(final boolean focused) {
        if (!focused)
            cancel();
        super.setFocused(focused);
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.literal(getMessage().getString() + ". Hold the left mouse button, Space, or Enter to operate. Release to cancel.");
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        final Minecraft client = Minecraft.getInstance();
        // Screen presses and releases are consumed before MouseHandler updates gameplay button state.
        if (holding && (!active || !client.isWindowActive()
                || (mouseGesture ? !isMouseOver(mouseX, mouseY) : !isFocused() || !InputConstants.isKeyDown(heldKey))))
            cancel();

        final float progress = holding ? Math.clamp((float) (System.nanoTime() - started) / durationNanos, 0, 1) : 0;
        if (holding)
            NovaRenderer.glow(graphics, getX(), getY(), width, height, 5, 3 + progress * 3, ACCENT);
        NovaRenderer.rect(graphics, getX(), getY(), width, height, 5, isHoveredOrFocused() && active ? BORDER : SURFACE);
        if (progress > 0)
            NovaRenderer.rect(graphics, getX() + 2, getY() + 2, (width - 4) * progress, height - 4, 4, BORDER);
        NovaRenderer.stroke(graphics, getX(), getY(), width, height, 5, holding || isFocused() ? 1.2F : 0.7F,
                holding || isFocused() ? ACCENT : BORDER);

        final String label = holding ? getMessage().getString() + " " + (int) (progress * 100) + "%" : "Hold: " + getMessage().getString();
        final float size = NovaRenderer.width(label, 9) <= width - 10 ? 9 : 7.5F;
        NovaRenderer.text(graphics, label, getX() + (width - NovaRenderer.width(label, size)) / 2,
                getY() + (height - 12) / 2F, size, active ? TEXT : MUTED);
        if (holding && progress >= 1) {
            holding = false;
            finishAction.run();
        }
    }
}
