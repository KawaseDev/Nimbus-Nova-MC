package cc.nimbusnova.mc.airlock;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record ScanPayload(boolean visible, int stage, int elapsed, boolean outbound, boolean helmet) implements CustomPacketPayload {
    public static final Type<ScanPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:entrance_scan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScanPayload> CODEC = CustomPacketPayload.codec(ScanPayload::write, ScanPayload::new);

    private ScanPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(visible);
        buffer.writeVarInt(stage);
        buffer.writeVarInt(elapsed);
        buffer.writeBoolean(outbound);
        buffer.writeBoolean(helmet);
    }

    @Override
    public @NonNull Type<ScanPayload> type() {
        return TYPE;
    }
}
