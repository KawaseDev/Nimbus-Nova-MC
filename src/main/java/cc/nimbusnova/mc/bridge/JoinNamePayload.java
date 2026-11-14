package cc.nimbusnova.mc.bridge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record JoinNamePayload(String name) implements CustomPacketPayload {
    public static final Type<JoinNamePayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:join_name"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JoinNamePayload> CODEC =
            CustomPacketPayload.codec((value, buffer) -> buffer.writeUtf(value.name, 32), buffer -> new JoinNamePayload(buffer.readUtf(32)));
    @Override public Type<JoinNamePayload> type() { return TYPE; }
}
