package cc.nimbusnova.mc.mission;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record MissionProgressPayload(String stage, String title, String instruction, String actionHint,
                                     int targetX, int targetY, int targetZ, int completed, int total,
                                     int taskStep, String feedback, boolean ready) implements CustomPacketPayload {
    public static final Type<MissionProgressPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:mission_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MissionProgressPayload> CODEC =
            CustomPacketPayload.codec(MissionProgressPayload::write, MissionProgressPayload::new);

    public boolean hasWorldTarget() {
        return switch (stage) {
            case "ARRIVAL", "ROLE", "TEAM", "PAUSED", "COMPLETE", "SHELTER_SAFE" -> false;
            default -> true;
        };
    }

    private MissionProgressPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(32), buffer.readUtf(96), buffer.readUtf(512), buffer.readUtf(128),
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readUtf(256), buffer.readBoolean());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(stage, 32);
        buffer.writeUtf(title, 96);
        buffer.writeUtf(instruction, 512);
        buffer.writeUtf(actionHint, 128);
        buffer.writeInt(targetX);
        buffer.writeInt(targetY);
        buffer.writeInt(targetZ);
        buffer.writeVarInt(completed);
        buffer.writeVarInt(total);
        buffer.writeVarInt(taskStep);
        buffer.writeUtf(feedback, 256);
        buffer.writeBoolean(ready);
    }

    @Override
    public @NonNull Type<MissionProgressPayload> type() {
        return TYPE;
    }
}
