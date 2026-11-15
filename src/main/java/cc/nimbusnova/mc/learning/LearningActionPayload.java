package cc.nimbusnova.mc.learning;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record LearningActionPayload(String source, int revision, int choice) implements CustomPacketPayload {
    public static final Type<LearningActionPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:learning_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LearningActionPayload> CODEC = CustomPacketPayload.codec(LearningActionPayload::write, LearningActionPayload::new);

    private LearningActionPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(16), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(source, 16);
        buffer.writeVarInt(revision);
        buffer.writeVarInt(choice);
    }

    @Override
    public @NonNull Type<LearningActionPayload> type() {
        return TYPE;
    }
}
