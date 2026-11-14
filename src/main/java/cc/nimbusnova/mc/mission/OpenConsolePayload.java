package cc.nimbusnova.mc.mission;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record OpenConsolePayload() implements CustomPacketPayload {
    public static final Type<OpenConsolePayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:open_console"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenConsolePayload> CODEC = StreamCodec.unit(new OpenConsolePayload());

    @Override
    public @NonNull Type<OpenConsolePayload> type() {
        return TYPE;
    }
}
