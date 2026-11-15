package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MissionButton extends Button implements MissionPalette {
    public boolean selected;
    private ItemStack icon = ItemStack.EMPTY;

    public MissionButton(final int x, final int y, final int width, final int height, final String text, final Runnable action) {
        super(x, y, width, height, Component.literal(text), _ -> action.run(), DEFAULT_NARRATION);
    }

    public MissionButton withIcon(final ItemLike item) {
        icon = new ItemStack(item);
        return this;
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        NovaRenderer.button(graphics, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), selected);
        final int inset = icon.isEmpty() ? 0 : 27;
        if (!icon.isEmpty()) {
            final int slotY = getY() + (getHeight() - 20) / 2;
            NovaRenderer.slot(graphics, getX() + 3, slotY, 20, 20, SURFACE, false);
            graphics.item(icon, getX() + 5, slotY + 2);
        }

        NovaRenderer.centered(graphics, getMessage().getString(), getX() + inset, getY(), getWidth() - inset, getHeight(),
                !active ? MUTED : selected ? ACCENT : TEXT);
    }
}
