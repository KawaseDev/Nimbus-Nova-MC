package cc.nimbusnova.mc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class MissionButton extends Button implements MissionPalette {
    boolean selected;

    MissionButton(final int x, final int y, final int width, final int height, final String text, final Runnable action) {
        super(x, y, width, height, Component.literal(text), _ -> action.run(), DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), (isHoveredOrFocused() && active ? BORDER : SURFACE).getRGB());
        graphics.outline(getX(), getY(), getWidth(), getHeight(), (selected || isFocused() ? ACCENT : BORDER).getRGB());
        graphics.text(Minecraft.getInstance().font, getMessage(),
                getX() + (getWidth() - Minecraft.getInstance().font.width(getMessage())) / 2,
                getY() + (getHeight() - 8) / 2, (active ? TEXT : MUTED).getRGB(), false);
    }
}
