package cc.nimbusnova.mc.scenario;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record ScenarioPayload(String json, boolean open) implements CustomPacketPayload {
    public static final Type<ScenarioPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:scenario"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScenarioPayload> CODEC = CustomPacketPayload.codec(
            (packet, buffer) -> { buffer.writeUtf(packet.json, 8192); buffer.writeBoolean(packet.open); },
            buffer -> new ScenarioPayload(buffer.readUtf(8192), buffer.readBoolean()));
    @Override
    public @NonNull Type<ScenarioPayload> type() {
        return TYPE;
    }
}
