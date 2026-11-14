package cc.nimbusnova.mc.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class JoinSessionScreen extends Screen {
    private final Screen parent;

    JoinSessionScreen(Screen parent) {
        super(Component.literal("Join session"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int top = height / 2 - 55;
        EditBox nickname = new EditBox(font, left, top, 200, 20, Component.literal("Nickname"));
        nickname.setHint(Component.literal("Nickname"));
        nickname.setMaxLength(32);
        addRenderableWidget(nickname);

        EditBox code = new EditBox(font, left, top + 30, 200, 20, Component.literal("Session code"));
        code.setHint(Component.literal("Session code"));
        code.setMaxLength(16);
        addRenderableWidget(code);

        // shortcut: joining is disabled until session code resolution and server allocation exist.
        Button join = Button.builder(Component.literal("Joining unavailable"), button -> {})
                .bounds(left, top + 60, 200, 20).build();
        join.active = false;
        addRenderableWidget(join);
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(left, top + 85, 200, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
