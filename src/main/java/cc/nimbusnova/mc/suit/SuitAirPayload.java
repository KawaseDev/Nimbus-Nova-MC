package cc.nimbusnova.mc.suit;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record SuitAirPayload(double air, boolean pressurized, boolean sealedSuit, boolean helmet, boolean frozen)
        implements CustomPacketPayload {
    public static final Type<SuitAirPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:suit_air"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuitAirPayload> CODEC =
            CustomPacketPayload.codec(SuitAirPayload::write, SuitAirPayload::new);

    private SuitAirPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(air);
        buffer.writeBoolean(pressurized);
        buffer.writeBoolean(sealedSuit);
        buffer.writeBoolean(helmet);
        buffer.writeBoolean(frozen);
    }

    @Override
    public @NonNull Type<SuitAirPayload> type() {
        return TYPE;
    }
}
