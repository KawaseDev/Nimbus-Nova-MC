package cc.nimbusnova.mc.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record ShipPayload(String phase, String stage, String role, int revision, int seconds,
                          int battery, int oxygen, int water, int fuel, int parts, int co2,
                          String title, String instruction, String feedback, String incident,
                          String task, int board, int aux, boolean complete,
                          BlockPos station, BlockPos target, boolean open, boolean operator) implements CustomPacketPayload {
    public static final Type<ShipPayload> TYPE = new Type<>(Identifier.parse("nimbus_nova:ship_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipPayload> CODEC = CustomPacketPayload.codec(ShipPayload::write, ShipPayload::new);

    private ShipPayload(final RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(32), buffer.readUtf(32), buffer.readUtf(32), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(128), buffer.readUtf(512), buffer.readUtf(512), buffer.readUtf(32), buffer.readUtf(32),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBlockPos(), buffer.readBlockPos(), buffer.readBoolean(), buffer.readBoolean());
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(phase, 32);
        buffer.writeUtf(stage, 32);
        buffer.writeUtf(role, 32);
        buffer.writeVarInt(revision);
        buffer.writeVarInt(seconds);
        buffer.writeVarInt(battery);
        buffer.writeVarInt(oxygen);
        buffer.writeVarInt(water);
        buffer.writeVarInt(fuel);
        buffer.writeVarInt(parts);
        buffer.writeVarInt(co2);
        buffer.writeUtf(title, 128);
        buffer.writeUtf(instruction, 512);
        buffer.writeUtf(feedback, 512);
        buffer.writeUtf(incident, 32);
        buffer.writeUtf(task, 32);
        buffer.writeVarInt(board);
        buffer.writeVarInt(aux);
        buffer.writeBoolean(complete);
        buffer.writeBlockPos(station);
        buffer.writeBlockPos(target);
        buffer.writeBoolean(open);
        buffer.writeBoolean(operator);
    }

    @Override
    public @NonNull Type<ShipPayload> type() {
        return TYPE;
    }
}
