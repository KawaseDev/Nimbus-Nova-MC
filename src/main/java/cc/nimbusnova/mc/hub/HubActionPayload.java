package cc.nimbusnova.mc.hub;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record HubActionPayload(String action, String mission) implements CustomPacketPayload {
    public static final Type<HubActionPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:hub_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HubActionPayload> CODEC = CustomPacketPayload.codec(
            (packet, buffer) -> { buffer.writeUtf(packet.action, 32); buffer.writeUtf(packet.mission, 32); },
            buffer -> new HubActionPayload(buffer.readUtf(32), buffer.readUtf(32)));

    @Override
    public @NonNull Type<HubActionPayload> type() {
        return TYPE;
    }
}
