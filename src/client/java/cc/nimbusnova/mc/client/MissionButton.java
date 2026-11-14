package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import java.awt.Color;

@NullMarked
public final class MissionButton extends Button implements MissionPalette {
    public boolean selected;

    public MissionButton(final int x, final int y, final int width, final int height, final String text, final Runnable action) {
        super(x, y, width, height, Component.literal(text), _ -> action.run(), DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        if (selected || isFocused())
            NovaRenderer.glow(graphics, getX(), getY(), getWidth(), getHeight(), 6, 3, new Color(239, 177, 94, 35));

        NovaRenderer.rect(graphics, getX(), getY(), getWidth(), getHeight(), 6, isHoveredOrFocused() && active ? BORDER : SURFACE);
        NovaRenderer.stroke(graphics, getX(), getY(), getWidth(), getHeight(), 6, 0.7F, selected || isFocused() ? ACCENT : BORDER);
        NovaRenderer.text(graphics, getMessage().getString(), getX() + (getWidth() - NovaRenderer.width(getMessage().getString(), 10)) / 2,
                getY() + (getHeight() - 13) / 2F, 10, active ? TEXT : MUTED);
    }
}
