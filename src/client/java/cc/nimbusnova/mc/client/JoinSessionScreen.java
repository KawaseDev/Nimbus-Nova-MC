package cc.nimbusnova.mc.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class JoinSessionScreen extends Screen {
    private final Screen parent;

    JoinSessionScreen(final Screen parent) {
        super(Component.literal("Join session"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100, top = height / 2 - 50;
        EditBox nickname = new EditBox(font, left, top, 200, 20, Component.literal("Nickname"));
        nickname.setMaxLength(32);
        addRenderableWidget(nickname);
        EditBox code = new EditBox(font, left, top + 40, 200, 20, Component.literal("Session code"));
        code.setMaxLength(16);
        addRenderableWidget(code);

        // shortcut: joining is disabled until session code resolution and server allocation exist.
        Button join = Button.builder(Component.literal("Joining unavailable"), button -> {})
                .bounds(left, top + 70, 200, 20).build();
        join.active = false;
        addRenderableWidget(join);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left, top + 94, 200, 20).build());
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = width / 2 - 100, top = height / 2 - 50;
        graphics.centeredText(font, title, width / 2, top - 32, 0xFFFFFFFF);
        graphics.text(font, "Nickname", left, top - 12, 0xFFA0A0A0);
        graphics.text(font, "Session code", left, top + 28, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
