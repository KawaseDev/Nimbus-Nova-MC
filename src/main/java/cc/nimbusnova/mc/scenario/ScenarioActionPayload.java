package cc.nimbusnova.mc.scenario;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record ScenarioActionPayload(String action, int station, int value, int revision) implements CustomPacketPayload {
    public static final Type<ScenarioActionPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:scenario_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScenarioActionPayload> CODEC = CustomPacketPayload.codec(
            (packet, buffer) -> { buffer.writeUtf(packet.action, 32); buffer.writeVarInt(packet.station); buffer.writeVarInt(packet.value); buffer.writeVarInt(packet.revision); },
            buffer -> new ScenarioActionPayload(buffer.readUtf(32), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
    @Override
    public @NonNull Type<ScenarioActionPayload> type() {
        return TYPE;
    }
}
