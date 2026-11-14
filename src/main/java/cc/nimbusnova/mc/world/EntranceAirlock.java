package cc.nimbusnova.mc.world;

import cc.nimbusnova.mc.airlock.ScanCycle;
import cc.nimbusnova.mc.airlock.ScanPayload;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.content.ScifiBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

@NullMarked
public final class EntranceAirlock {
    private static final Map<ServerLevel, ScanCycle> CYCLES = new IdentityHashMap<>();
    private static final AABB CHAMBER = new AABB(-2, 66, -41.3, 2, 70, -34.7);
    private static final AABB SCAN_AREA = new AABB(-2, 66, -40, 2, 70, -36);
    private static final AABB INSIDE_APPROACH = new AABB(-2, 66, -34.7, 2, 70, -32);

    private EntranceAirlock() {
        /* w */
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ScanPayload.TYPE, ScanPayload.CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CYCLES.keySet().removeIf(level -> level.getServer() == server));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            final ServerLevel level = server.getLevel(MarsTrainingMap.DIMENSION);
            if (level == null || !isInstalled(level)) return;

            final ScanCycle cycle = CYCLES.computeIfAbsent(level, _ -> new ScanCycle());
            final boolean occupied = level.players().stream().anyMatch(player -> !player.isSpectator() && CHAMBER.contains(player.position()));
            final ServerPlayer entrant = level.players().stream().filter(player -> !player.isSpectator() && SCAN_AREA.contains(player.position())).findFirst().orElse(null);
            final boolean fullyInside = entrant != null;
            final boolean approaching = entrant != null ? entrant.getZ() > -38
                    : level.players().stream().anyMatch(player -> !player.isSpectator() && INSIDE_APPROACH.contains(player.position()));
            final int previousStage = cycle.stage();
            cycle.tick(occupied, fullyInside, approaching);
            if (previousStage != cycle.stage() && cycle.stage() != 0)
                level.playSound(null, 0, 67, -38, cycle.stage() == 1 ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.BLOCKS, 0.5F, cycle.stage() == 1 ? 0.7F : 1.3F);

            if (server.getTickCount() % 5 != 0) return;

            for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
                final boolean visible = player.level() == level && player.distanceToSqr(0, 67, -38) < 12 * 12;
                ServerPlayNetworking.send(player, new ScanPayload(visible, cycle.stage(), cycle.elapsed(), cycle.outbound(),
                        player.getItemBySlot(EquipmentSlot.HEAD).is(ModContent.SPACE_HELMET)));
            }

            if (cycle.stage() == 1) {
                for (final int wallX : new int[] { -2, 2 })
                    level.sendParticles(ParticleTypes.CLOUD, wallX, 67.2, -38, 2, 0.1, 0.4, 1.2, 0.03);
            }
        });
    }

    public static boolean isInstalled(final ServerLevel level) {
        return level.getBlockState(new BlockPos(-3, 66, -38)).is(ScifiBlocks.REINFORCED_HULL)
                && level.getBlockState(new BlockPos(0, 66, -35)).is(ScifiBlocks.AIRLOCK_DOOR);
    }

    public static boolean isChamber(final BlockPos position) {
        return position.getX() >= -2 && position.getX() <= 1 && position.getY() >= 66 && position.getY() <= 69
                && position.getZ() >= -41 && position.getZ() <= -35;
    }

    public static boolean pressurized(final ServerLevel level, final BlockPos position) {
        if (!isInstalled(level) || !isChamber(position)) return false;
        final ScanCycle cycle = CYCLES.get(level);
        return cycle != null && cycle.innerOpen();
    }

    public static @Nullable Boolean openingOverride(final ServerLevel level, final BlockPos position) {
        if (!isInstalled(level) || position.getX() < -1 || position.getX() > 0
                || position.getY() < 66 || position.getY() > 67 || position.getZ() != -41 && position.getZ() != -35) return null;

        final ScanCycle cycle = CYCLES.computeIfAbsent(level, _ -> new ScanCycle());
        final boolean allowed = position.getZ() == -41 ? cycle.outerOpen() : cycle.innerOpen();
        return allowed && level.players().stream().anyMatch(player -> player.isAlive() && !player.isSpectator()
                && player.distanceToSqr(position.getX() + 0.5, 67, position.getZ() + 0.5) < 3.5 * 3.5);
    }

    public static @Nullable BlockPos findConflict(final ServerLevel level) {
        if (isInstalled(level)) return null;
        for (final BlockPos position : BlockPos.betweenClosed(-4, 65, -46, 4, 71, -34)) {
            final BlockState state = level.getBlockState(position);
            if (state.isAir()) continue;
            if (position.getY() == 65 && (state.is(Blocks.CONCRETE.gray()) || state.is(Blocks.CONCRETE.black())
                    || state.is(Blocks.CONCRETE.white()) || state.is(ScifiBlocks.FLOOR_TILE))) continue;
            if (position.getZ() >= -36 && (state.is(ScifiBlocks.DOME_GLASS) || state.is(ScifiBlocks.SUPPORT_BEAM)
                    || state.is(ScifiBlocks.REINFORCED_HULL) || state.is(ScifiBlocks.FLOOR_LIGHT))) continue;
            return position.immutable();
        }

        return null;
    }

    public static void build(final ServerLevel level) {
        if (isInstalled(level)) return;

        for (final BlockPos position : BlockPos.betweenClosed(-3, 65, -46, 2, 65, -34))
            level.setBlock(position, ScifiBlocks.FLOOR_TILE.defaultBlockState(), Block.UPDATE_CLIENTS);

        for (final BlockPos position : BlockPos.betweenClosed(-3, 66, -42, 2, 70, -34)) {
            final boolean wall = position.getX() == -3 || position.getX() == 2 || position.getY() == 70
                    || position.getZ() == -42 || position.getZ() == -34;
            final boolean gate = position.getX() >= -1 && position.getX() <= 0 && position.getY() <= 67
                    && (position.getZ() == -42 || position.getZ() == -34);
            level.setBlock(position, (wall && !gate ? ScifiBlocks.REINFORCED_HULL : Blocks.AIR).defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        for (final int wallX : new int[] { -3, 2 }) {
            for (int z = -40; z <= -36; z++)
                level.setBlock(new BlockPos(wallX, 68, z), ScifiBlocks.DOME_GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        for (final int z : new int[] { -41, -35 }) {
            for (final BlockPos position : BlockPos.betweenClosed(-3, 66, z, 2, 69, z)) {
                if (position.getX() >= -1 && position.getX() <= 0 && position.getY() <= 67) continue;
                level.setBlock(position, ScifiBlocks.REINFORCED_HULL.defaultBlockState(), Block.UPDATE_CLIENTS);
            }

            for (int x = -1; x <= 0; x++) {
                final BlockState door = ScifiBlocks.AIRLOCK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                        .setValue(DoorBlock.HINGE, x == -1 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
                level.setBlock(new BlockPos(x, 66, z), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x, 67, z), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
                level.scheduleTick(new BlockPos(x, 66, z), ScifiBlocks.AIRLOCK_DOOR, 5);
            }
        }

        for (final int z : new int[] { -40, -38, -36 })
            level.setBlock(new BlockPos(-1, 70, z), ScifiBlocks.FLOOR_LIGHT.defaultBlockState(), Block.UPDATE_CLIENTS);

        // Funnel the wider dome portal into the interlocked passage without rebuilding the shell.
        for (int x = -4; x <= 4; x++) {
            if (x >= -1 && x <= 0) continue;
            for (int y = 66; y <= 69; y++)
                level.setBlock(new BlockPos(x, y, -34), ScifiBlocks.REINFORCED_HULL.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
