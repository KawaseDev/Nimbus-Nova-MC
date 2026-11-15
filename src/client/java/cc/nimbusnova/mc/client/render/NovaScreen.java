package cc.nimbusnova.mc.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class NovaScreen extends Screen {
    protected NovaScreen(final Component title) {
        super(title);
    }

    @Override
    protected void rebuildWidgets() {
        final var focus = getFocused();
        super.rebuildWidgets();
        if (!(focus instanceof Button previous)) return;

        // Live telemetry can rebuild a screen between keyboard inputs.
        for (final var child : children()) {
            if (child instanceof Button button && button.active && button.visible && button.getClass() == previous.getClass()
                    && button.getX() == previous.getX() && button.getY() == previous.getY()
                    && button.getMessage().equals(previous.getMessage())) {
                setFocused(button);
                break;
            }
        }
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        NovaRenderer.blur(graphics);
    }
}
