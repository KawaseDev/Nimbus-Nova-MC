package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.NullMarked;

import java.awt.Color;

@NullMarked
public final class MissionButton extends Button implements MissionPalette {
    public boolean selected;
    private ItemStack icon = ItemStack.EMPTY;
    private NovaRenderer.Tone tone = NovaRenderer.Tone.NORMAL;
    private boolean leftAligned;

    public MissionButton(final int x, final int y, final int width, final int height, final String text, final Runnable action) {
        super(x, y, width, height, Component.literal(text), _ -> action.run(), DEFAULT_NARRATION);
    }

    public MissionButton withIcon(final ItemLike item) {
        icon = new ItemStack(item);
        return this;
    }

    public MissionButton withTone(final NovaRenderer.Tone tone) {
        this.tone = tone;
        return this;
    }

    public MissionButton alignLeft() {
        leftAligned = true;
        return this;
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        NovaRenderer.button(graphics, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), selected, tone);
        final int inset = icon.isEmpty() ? 0 : 22;
        if (!icon.isEmpty())
            graphics.item(icon, getX() + 4, getY() + (getHeight() - 16) / 2);

        final Color color = !active ? MUTED : selected ? ACCENT : TEXT;
        if (leftAligned && NovaRenderer.width(getMessage().getString(), 10) <= getWidth() - inset - 8) {
            NovaRenderer.text(graphics, getMessage().getString(), getX() + inset + 4, getY() + (getHeight() - 9) / 2F, 10, color);
            return;
        }

        NovaRenderer.centered(graphics, getMessage().getString(), getX() + inset, getY() - 1, getWidth() - inset, getHeight(), color);
    }
}
