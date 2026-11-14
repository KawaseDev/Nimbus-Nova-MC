package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class JoinSessionScreen extends NovaScreen implements MissionPalette {
    private final Screen parent;

    JoinSessionScreen(final Screen parent) {
        super(Component.literal("Join session"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int left = width / 2 - 110, top = height / 2 - 30;
        addRenderableWidget(new MissionEditBox(left, top, 220, "Nickname", 32));
        addRenderableWidget(new MissionEditBox(left, top + 34, 220, "Session code", 16));

        // shortcut: joining is disabled until session code resolution and server allocation exist.
        final MissionButton join = new MissionButton(left, top + 70, 220, 22, "Joining unavailable", () -> {
            /* w */
        });
        join.active = false;
        addRenderableWidget(join);
        addRenderableWidget(new MissionButton(left, top + 98, 220, 22, "Back", this::onClose));
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        MissionUi.panel(graphics, width / 2 - 130, height / 2 - 94, 260, 200);
        NovaRenderer.text(graphics, "Join your crew", width / 2F - 110, height / 2F - 79, 18, TEXT);
        MissionUi.wrap(graphics, "Enter your classroom details. Session connection is in development.", width / 2 - 110,
                height / 2 - 54, 220, 2, MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
