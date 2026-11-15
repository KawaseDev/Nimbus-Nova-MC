package cc.nimbusnova.mc.client.ship.rooms;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.client.ship.ShipPalette;
import cc.nimbusnova.mc.ship.rooms.RoomActionPayload;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RoomScreen extends NovaScreen implements ShipPalette {
    private JsonObject state;
    private int page, left, top, panelWidth, panelHeight;

    public RoomScreen(final JsonObject state) {
        super(Component.literal("Ship room control"));
        this.state = state;
    }

    public JsonObject state() {
        return state;
    }

    public void update(final JsonObject state) {
        this.state = state;
        rebuildWidgets();
    }

    private void send(final String room, final String action) {
        ClientPlayNetworking.send(new RoomActionPayload(room, action));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(590, width - 16);
        panelHeight = Math.min(300, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        final var rooms = state.getAsJsonArray("rooms");
        page = Math.clamp(page, 0, Math.max(0, (rooms.size() - 1) / 4));
        for (int index = page * 4; index < Math.min(rooms.size(), page * 4 + 4); index++) {
            final JsonObject room = rooms.get(index).getAsJsonObject();
            final String id = room.get("id").getAsString();
            final int rowTop = top + 61 + (index % 4) * 27;
            for (int control = 0; control < 3; control++) {
                final String mode = new String[] { "auto", "open", "close" }[control];
                addRenderableWidget(new MissionButton(left + panelWidth - 145 + control * 44, rowTop, 41, 22,
                        mode, () -> send(id, mode)));
            }
        }
        if (state.get("operator").getAsBoolean()) {
            addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 55, 95, 20,
                    "Power drill", () -> send("ENGINEERING", "power")));
            addRenderableWidget(new MissionButton(left + 114, top + panelHeight - 55, 95, 20,
                    "Breach drill", () -> send("SCIENCE", "breach")));
        }
        addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 29, 38, 20, "<", () -> { page--; rebuildWidgets(); })).active = page > 0;
        addRenderableWidget(new MissionButton(left + 57, top + panelHeight - 29, 38, 20, ">", () -> { page++; rebuildWidgets(); })).active = (page + 1) * 4 < rooms.size();
        addRenderableWidget(new MissionButton(left + panelWidth - 70, top + panelHeight - 29, 56, 20, "Close", this::onClose));
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        NovaRenderer.rect(graphics, left, top, panelWidth, panelHeight, 9, BACKGROUND);
        NovaRenderer.text(graphics, "Compartment control", left + 14, top + 12, 15, TEXT);
        final String alert = state.get("alert").getAsString();
        NovaRenderer.wrap(graphics, alert.isEmpty() ? "Select door mode: AUTO uses proximity and pressure interlocks."
                : alert + ". " + state.get("instruction").getAsString(), left + 14, top + 34, panelWidth - 28, 8, 2, alert.isEmpty() ? MUTED : WARNING);
        final var rooms = state.getAsJsonArray("rooms");
        for (int index = page * 4; index < Math.min(rooms.size(), page * 4 + 4); index++) {
            final JsonObject room = rooms.get(index).getAsJsonObject();
            final int rowTop = top + 63 + index % 4 * 27;
            final boolean powered = room.get("powered").getAsBoolean();
            final String fault = room.get("fault").getAsString();
            NovaRenderer.text(graphics, room.get("name").getAsString(), left + 14, rowTop, 9, TEXT);
            NovaRenderer.text(graphics, "Pressure " + room.get("pressure").getAsInt() + "% / " + (powered ? "power ON" : "power OFF")
                    + " / " + room.get("mode").getAsString(), left + 14, rowTop + 11, 7, fault.equals("NONE") && powered && room.get("pressure").getAsInt() >= 80 ? MUTED : WARNING);
        }
        NovaRenderer.text(graphics, "Page " + (page + 1), left + 105, top + panelHeight - 23, 8, MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
