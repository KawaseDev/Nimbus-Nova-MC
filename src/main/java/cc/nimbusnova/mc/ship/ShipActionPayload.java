package cc.nimbusnova.mc.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record ShipActionPayload(String action, BlockPos position, int revision, int value) implements CustomPacketPayload {
    public static final Type<ShipActionPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:ship_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipActionPayload> CODEC = CustomPacketPayload.codec(ShipActionPayload::write, ShipActionPayload::new);

    private ShipActionPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(32), buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(action, 32);
        buffer.writeBlockPos(position);
        buffer.writeVarInt(revision);
        buffer.writeVarInt(value);
    }

    @Override
    public @NonNull Type<ShipActionPayload> type() {
        return TYPE;
    }
}
