package cc.nimbusnova.mc.bridge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record ViewRequestPayload(UUID id) implements CustomPacketPayload {
    public static final Type<ViewRequestPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:view_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ViewRequestPayload> CODEC =
            CustomPacketPayload.codec((value, buffer) -> buffer.writeUUID(value.id), buffer -> new ViewRequestPayload(buffer.readUUID()));
    @Override public Type<ViewRequestPayload> type() { return TYPE; }
}
