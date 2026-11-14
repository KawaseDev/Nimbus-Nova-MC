package cc.nimbusnova.mc.npc;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record NpcDialoguePayload(String name, String briefing, String powerHint, String oxygenHint) implements CustomPacketPayload {
    public static final Type<NpcDialoguePayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:npc_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialoguePayload> CODEC =
            CustomPacketPayload.codec(NpcDialoguePayload::write, NpcDialoguePayload::new);

    private NpcDialoguePayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(64), buffer.readUtf(1024), buffer.readUtf(1024), buffer.readUtf(1024));
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(name, 64);
        buffer.writeUtf(briefing, 1024);
        buffer.writeUtf(powerHint, 1024);
        buffer.writeUtf(oxygenHint, 1024);
    }

    @Override
    public @NonNull Type<NpcDialoguePayload> type() {
        return TYPE;
    }
}
