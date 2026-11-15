package cc.nimbusnova.mc.learning;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record LearningPayload(String source, String incident, int revision, String readings,
                              int firstValue, int secondValue, String firstLabel, String secondLabel,
                              boolean solved, String feedback, boolean open) implements CustomPacketPayload {
    public static final Type<LearningPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:learning_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LearningPayload> CODEC = CustomPacketPayload.codec(LearningPayload::write, LearningPayload::new);

    private LearningPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(16), buffer.readUtf(32), buffer.readVarInt(), buffer.readUtf(512), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(64), buffer.readUtf(64), buffer.readBoolean(), buffer.readUtf(512), buffer.readBoolean());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(source, 16);
        buffer.writeUtf(incident, 32);
        buffer.writeVarInt(revision);
        buffer.writeUtf(readings, 512);
        buffer.writeVarInt(firstValue);
        buffer.writeVarInt(secondValue);
        buffer.writeUtf(firstLabel, 64);
        buffer.writeUtf(secondLabel, 64);
        buffer.writeBoolean(solved);
        buffer.writeUtf(feedback, 512);
        buffer.writeBoolean(open);
    }

    @Override
    public @NonNull Type<LearningPayload> type() {
        return TYPE;
    }
}
