package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.awt.Color;

@NullMarked
final class MissionEditBox extends EditBox implements MissionPalette {
    private final String placeholder;
    private int selectionAnchor;
    private float scroll;

    MissionEditBox(final int x, final int y, final int width, final String placeholder, final int maxLength) {
        super(Minecraft.getInstance().font, x, y, width, 26, Component.literal(placeholder));
        this.placeholder = placeholder;
        setMaxLength(maxLength);
        setBordered(false);
    }

    @Override
    public void setHighlightPos(final int position) {
        super.setHighlightPos(position);
        selectionAnchor = position;
    }

    @Override
    public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
        moveCursorTo(positionAt(event.x()), Minecraft.getInstance().hasShiftDown());
    }

    @Override
    protected void onDrag(final MouseButtonEvent event, final double deltaX, final double deltaY) {
        moveCursorTo(positionAt(event.x()), true);
    }

    private int positionAt(final double mouseX) {
        final String value = getValue();
        final float target = (float) mouseX - getX() - 9 + scroll;
        int position = 0;
        while (position < value.length()) {
            final int next = value.offsetByCodePoints(position, 1);
            if (target < (NovaRenderer.width(value.substring(0, position), 10) + NovaRenderer.width(value.substring(0, next), 10)) / 2) break;
            position = next;
        }

        return position;
    }

    @Override
    public void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        if (!isVisible()) return;

        NovaRenderer.slot(graphics, getX(), getY(), getWidth(), getHeight(), SURFACE, isFocused());
        final String value = getValue();
        final int cursor = Math.clamp(getCursorPosition(), 0, value.length());
        final float cursorX = NovaRenderer.width(value.substring(0, cursor), 10), available = getWidth() - 20;
        scroll = Math.clamp(scroll, Math.max(0, cursorX - available), cursorX);
        graphics.enableScissor(getX() + 6, getY() + 2, getRight() - 6, getBottom() - 2);

        final int start = Math.clamp(Math.min(cursor, selectionAnchor), 0, value.length());
        final int end = Math.clamp(Math.max(cursor, selectionAnchor), 0, value.length());
        if (isFocused() && start != end)
            NovaRenderer.rect(graphics, getX() + 9 - scroll + NovaRenderer.width(value.substring(0, start), 10), getY() + 5,
                    NovaRenderer.width(value.substring(start, end), 10), 16, new Color(239, 177, 94, 65));

        NovaRenderer.text(graphics, value.isEmpty() ? placeholder : value, getX() + 9 - scroll, getY() + 8, 10, value.isEmpty() ? MUTED : TEXT);
        if (isFocused())
            NovaRenderer.rect(graphics, getX() + 9 - scroll + cursorX, getY() + 6, 0.7F, 13, ACCENT);

        graphics.disableScissor();
    }
}
