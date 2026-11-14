package cc.nimbusnova.mc.lesson;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record LessonActionPayload(BlockPos position, int revision, int action) implements CustomPacketPayload {
    public static final Type<LessonActionPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:lesson_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LessonActionPayload> CODEC = CustomPacketPayload.codec(LessonActionPayload::write, LessonActionPayload::new);

    private LessonActionPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeVarInt(revision);
        buffer.writeVarInt(action);
    }

    @Override
    public @NonNull Type<LessonActionPayload> type() {
        return TYPE;
    }
}
