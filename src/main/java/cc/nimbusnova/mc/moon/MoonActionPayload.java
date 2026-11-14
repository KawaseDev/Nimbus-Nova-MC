package cc.nimbusnova.mc.moon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record MoonActionPayload(String action) implements CustomPacketPayload {
    public static final Type<MoonActionPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:moon_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoonActionPayload> CODEC = CustomPacketPayload.codec(
            (packet, buffer) -> buffer.writeUtf(packet.action, 32), buffer -> new MoonActionPayload(buffer.readUtf(32)));
    @Override public @NonNull Type<MoonActionPayload> type() { return TYPE; }
}
