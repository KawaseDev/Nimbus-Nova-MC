package cc.nimbusnova.mc.moon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record MoonPayload(String variant, String stage, String incident, String role, boolean paused,
                          boolean acknowledged, boolean decisionMade, int claimed, int confirmed,
                          int battery, int oxygen, int water, int fuel, int parts, String feedback, boolean open)
        implements CustomPacketPayload {
    public static final Type<MoonPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:moon_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoonPayload> CODEC = CustomPacketPayload.codec(MoonPayload::write, MoonPayload::new);

    private MoonPayload(RegistryFriendlyByteBuf b) {
        this(b.readUtf(16), b.readUtf(16), b.readUtf(16), b.readUtf(16), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(256), b.readBoolean());
    }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeUtf(variant, 16); b.writeUtf(stage, 16); b.writeUtf(incident, 16); b.writeUtf(role, 16);
        b.writeBoolean(paused); b.writeBoolean(acknowledged); b.writeBoolean(decisionMade);
        b.writeVarInt(claimed); b.writeVarInt(confirmed); b.writeVarInt(battery); b.writeVarInt(oxygen);
        b.writeVarInt(water); b.writeVarInt(fuel); b.writeVarInt(parts); b.writeUtf(feedback, 256); b.writeBoolean(open);
    }
    @Override public @NonNull Type<MoonPayload> type() { return TYPE; }
}
