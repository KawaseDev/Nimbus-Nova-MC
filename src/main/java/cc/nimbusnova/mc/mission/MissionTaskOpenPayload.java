package cc.nimbusnova.mc.mission;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record MissionTaskOpenPayload(MissionPreparation.TaskKind kind, int step, int puzzleState, int aux) implements CustomPacketPayload {
    public static final Type<MissionTaskOpenPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:mission_task_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MissionTaskOpenPayload> CODEC =
            CustomPacketPayload.codec((packet, buffer) -> {
                buffer.writeUtf(packet.kind.name(), 32);
                buffer.writeVarInt(packet.step);
                buffer.writeInt(packet.puzzleState);
                buffer.writeInt(packet.aux);
            }, buffer -> new MissionTaskOpenPayload(MissionPreparation.TaskKind.valueOf(buffer.readUtf(32)), buffer.readVarInt(), buffer.readInt(), buffer.readInt()));

    @Override
    public @NonNull Type<MissionTaskOpenPayload> type() {
        return TYPE;
    }

    public boolean complete() {
        return step < 0;
    }
}
