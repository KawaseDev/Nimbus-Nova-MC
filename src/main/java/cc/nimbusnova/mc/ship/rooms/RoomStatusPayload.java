package cc.nimbusnova.mc.ship.rooms;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record RoomStatusPayload(String json, boolean open) implements CustomPacketPayload {
    public static final Type<RoomStatusPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:room_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoomStatusPayload> CODEC = CustomPacketPayload.codec(RoomStatusPayload::write, RoomStatusPayload::new);

    private RoomStatusPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(8192), buffer.readBoolean());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(json, 8192);
        buffer.writeBoolean(open);
    }

    @Override
    public @NonNull Type<RoomStatusPayload> type() {
        return TYPE;
    }
}
