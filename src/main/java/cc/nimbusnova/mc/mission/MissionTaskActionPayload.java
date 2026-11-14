package cc.nimbusnova.mc.mission;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record MissionTaskActionPayload(MissionPreparation.TaskKind kind, int step, int choice) implements CustomPacketPayload {
    public static final Type<MissionTaskActionPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:mission_task_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MissionTaskActionPayload> CODEC =
            CustomPacketPayload.codec((packet, buffer) -> {
                buffer.writeUtf(packet.kind.name(), 32);
                buffer.writeVarInt(packet.step);
                buffer.writeVarInt(packet.choice);
            }, buffer -> new MissionTaskActionPayload(MissionPreparation.TaskKind.valueOf(buffer.readUtf(32)),
                    buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public @NonNull Type<MissionTaskActionPayload> type() {
        return TYPE;
    }
}
