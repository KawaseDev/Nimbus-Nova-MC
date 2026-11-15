package cc.nimbusnova.mc.hub;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record HubStatePayload(String json, boolean open) implements CustomPacketPayload {
    public static final Type<HubStatePayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:hub_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HubStatePayload> CODEC = CustomPacketPayload.codec(
            (packet, buffer) -> { buffer.writeUtf(packet.json, 8192); buffer.writeBoolean(packet.open); },
            buffer -> new HubStatePayload(buffer.readUtf(8192), buffer.readBoolean()));

    @Override
    public @NonNull Type<HubStatePayload> type() {
        return TYPE;
    }
}
