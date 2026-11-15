package cc.nimbusnova.mc.ship.rooms;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record RoomActionPayload(String room, String action) implements CustomPacketPayload {
    public static final Type<RoomActionPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:room_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoomActionPayload> CODEC = CustomPacketPayload.codec(RoomActionPayload::write, RoomActionPayload::new);

    private RoomActionPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(32), buffer.readUtf(32));
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(room, 32);
        buffer.writeUtf(action, 32);
    }

    @Override
    public @NonNull Type<RoomActionPayload> type() {
        return TYPE;
    }
}
