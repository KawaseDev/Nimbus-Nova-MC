package cc.nimbusnova.mc.bridge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record ViewFramePayload(UUID id, byte[] image) implements CustomPacketPayload {
    public static final int MAX_BYTES = 200_000;
    public static final Type<ViewFramePayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:view_frame"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ViewFramePayload> CODEC =
            CustomPacketPayload.codec((value, buffer) -> { buffer.writeUUID(value.id); buffer.writeByteArray(value.image); },
                    buffer -> new ViewFramePayload(buffer.readUUID(), buffer.readByteArray(MAX_BYTES)));
    @Override public Type<ViewFramePayload> type() { return TYPE; }
}
