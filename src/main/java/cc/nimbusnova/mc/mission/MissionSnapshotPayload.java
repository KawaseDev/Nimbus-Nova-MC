package cc.nimbusnova.mc.mission;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record MissionSnapshotPayload(MissionState.Snapshot state, CrewRole role, boolean operator, int crew)
        implements CustomPacketPayload {
    public static final Type<MissionSnapshotPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:mission_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MissionSnapshotPayload> CODEC =
            CustomPacketPayload.codec(MissionSnapshotPayload::write, MissionSnapshotPayload::new);

    private MissionSnapshotPayload(final RegistryFriendlyByteBuf buffer) {
        this(new MissionState.Snapshot(MissionState.Phase.valueOf(buffer.readUtf(16)), buffer.readVarInt(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(512),
                        buffer.readDouble(), buffer.readDouble(), buffer.readVarInt()),
                CrewRole.valueOf(buffer.readUtf(16)), buffer.readBoolean(), buffer.readVarInt());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(state.phase().name(), 16);
        buffer.writeVarInt(state.elapsedSeconds());
        buffer.writeDouble(state.battery());
        buffer.writeDouble(state.oxygen());
        buffer.writeBoolean(state.oxygenOnline());
        buffer.writeBoolean(state.researchOnline());
        buffer.writeDouble(state.generation());
        buffer.writeDouble(state.demand());
        buffer.writeVarInt(state.decisions());
        buffer.writeVarInt(state.researchSeconds());
        buffer.writeUtf(state.lastEvent(), 512);
        buffer.writeDouble(state.lowestBattery());
        buffer.writeDouble(state.lowestOxygen());
        buffer.writeVarInt(state.firstResearchCutSecond());
        buffer.writeUtf(role.name(), 16);
        buffer.writeBoolean(operator);
        buffer.writeVarInt(crew);
    }

    @Override
    public @NonNull Type<MissionSnapshotPayload> type() {
        return TYPE;
    }
}
