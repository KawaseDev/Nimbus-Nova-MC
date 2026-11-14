package cc.nimbusnova.mc.lesson;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record LessonPayload(BlockPos position, int kind, int revision, int pressure, int target, boolean isolated,
                            int layers, int cells, int battery, String feedback) implements CustomPacketPayload {
    public static final Type<LessonPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:lesson_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LessonPayload> CODEC = CustomPacketPayload.codec(LessonPayload::write, LessonPayload::new);

    private LessonPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(256));
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeVarInt(kind);
        buffer.writeVarInt(revision);
        buffer.writeVarInt(pressure);
        buffer.writeVarInt(target);
        buffer.writeBoolean(isolated);
        buffer.writeVarInt(layers);
        buffer.writeVarInt(cells);
        buffer.writeVarInt(battery);
        buffer.writeUtf(feedback, 256);
    }

    @Override
    public @NonNull Type<LessonPayload> type() {
        return TYPE;
    }
}
