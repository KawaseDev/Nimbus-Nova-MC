package cc.nimbusnova.mc.world.ship;

import cc.nimbusnova.mc.NimbusNovaMod;
import cc.nimbusnova.mc.content.ShipContent;
import cc.nimbusnova.mc.content.ScifiBlocks;
import cc.nimbusnova.mc.ship.ShipTask;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.storage.LevelResource;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class ShipMap {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.parse("nimbus_nova:return_window"));
    public static final BlockPos SPAWN = new BlockPos(0, 80, 23);
    private static final Map<String, String> FALLBACKS = Map.of(
            "gray_wool_stairs", "polished_deepslate_stairs", "light_gray_wool_stairs", "polished_andesite_stairs",
            "light_gray_concrete_slab", "smooth_stone_slab", "white_concrete_slab", "smooth_quartz_slab", "iron_chain", "chain");
    public static final List<Marker> MARKERS = List.of(
            new Marker(new BlockPos(-9, 76, -19), ShipTask.COMMS), new Marker(new BlockPos(7, 76, -12), ShipTask.SCIENCE),
            new Marker(new BlockPos(-8, 76, 1), ShipTask.PLANTS), new Marker(new BlockPos(9, 76, 13), ShipTask.STORAGE),
            new Marker(new BlockPos(-5, 77, 12), ShipTask.EXERCISE), new Marker(new BlockPos(0, 80, 24), ShipTask.PILOT),
            new Marker(new BlockPos(8, 81, -8), ShipTask.MEETING), new Marker(new BlockPos(8, 81, 4), ShipTask.MEDICAL),
            new Marker(new BlockPos(-8, 82, -38), ShipTask.ENGINE), new Marker(new BlockPos(8, 82, -38), ShipTask.ENGINE),
            new Marker(new BlockPos(-8, 82, 15), ShipTask.GALLEY));

    private ShipMap() {
        /* w */
    }

    public static Block block(final ShipTask task) {
        return switch (task) {
            case PILOT -> ShipContent.PILOT_CONSOLE;
            case GALLEY -> ShipContent.GALLEY;
            case MEDICAL -> ShipContent.MEDICAL_STATION;
            case MEETING -> ShipContent.MEETING_TERMINAL;
            case ENGINE -> ShipContent.ENGINE_CORE;
            case SCIENCE -> ShipContent.SCIENCE_LAB;
            case COMMS -> ShipContent.COMMS_ARRAY;
            case PLANTS -> ShipContent.HYDROPONICS;
            case EXERCISE -> ShipContent.PUNCHING_BAG;
            case STORAGE -> ShipContent.STORAGE_LOCKER;
        };
    }

    public static ShipTask task(final Block block) {
        for (final ShipTask task : ShipTask.values())
            if (block(task) == block) return task;
        return null;
    }

    public static BlockPos target(final ShipTask task, final BlockPos from) {
        return MARKERS.stream().filter(marker -> marker.task == task)
                .min(Comparator.comparingDouble(marker -> marker.position.distSqr(from))).orElseThrow().position;
    }

    public static void create(final MinecraftServer server) throws IOException {
        final ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) throw new IOException("The Return Window dimension is unavailable. Restart with the updated mod.");
        final Path marker = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/return-window-v1.txt");
        if (Files.exists(marker)) {
            if (!Files.readString(marker).equals("complete")) throw new IOException("Ship construction was interrupted. Restore a backup or use a fresh world.");
            for (final Placed placed : read(server)) {
                final var current = level.getBlockState(placed.position);
                if ((current.is(ScifiBlocks.HULL_PANEL) || current.is(ScifiBlocks.FLOOR_TILE))
                        && Set.of(Blocks.CONCRETE.gray(), Blocks.CONCRETE.white(), Blocks.CONCRETE.lightGray(),
                        Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_DEEPSLATE, Blocks.WOOL.gray(), Blocks.SPRUCE_PLANKS, Blocks.MANGROVE_PLANKS)
                        .contains(placed.state.getBlock()))
                    level.setBlock(placed.position, placed.state, Block.UPDATE_CLIENTS);
            }
            installRoomPartition(level);
            exposeEngineControls(level);
            installWindows(level, read(server));
            return;
        }

        final List<Placed> blocks = read(server);
        for (final BlockPos position : BlockPos.betweenClosed(-13, 64, -41, 12, 94, 40))
            if (!level.getBlockState(position).isAir()) throw new IOException("Ship build stopped: existing blocks at " + position + " were preserved.");

        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "building");
        final Set<BlockPos> panels = new HashSet<>();
        for (final Placed placed : blocks) {
            final BlockPos position = placed.position;
            BlockState state = placed.state;
            if (state.is(Blocks.WOOL.lime())) {
                panels.add(position);
                continue;
            }
            if (state.hasProperty(BlockStateProperties.WATERLOGGED))
                state = state.setValue(BlockStateProperties.WATERLOGGED, false);
            if (state.getBlock() instanceof DoorBlock)
                state = ScifiBlocks.AIRLOCK_DOOR.withPropertiesOf(state);
            level.setBlock(position, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (state.is(ScifiBlocks.AIRLOCK_DOOR) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                level.scheduleTick(position, ScifiBlocks.AIRLOCK_DOOR, 5);
        }

        while (!panels.isEmpty()) {
            final Set<BlockPos> component = new HashSet<>();
            final List<BlockPos> pending = new ArrayList<>();
            final BlockPos first = panels.iterator().next();
            panels.remove(first);
            pending.add(first);
            for (int index = 0; index < pending.size(); index++) {
                final BlockPos position = pending.get(index);
                component.add(position);
                for (final Direction direction : Direction.values()) {
                    final BlockPos neighbor = position.relative(direction);
                    if (panels.remove(neighbor)) pending.add(neighbor);
                }
            }
            installPanel(level, component);
        }

        for (final Marker station : MARKERS) {
            final Direction facing = station.task == ShipTask.ENGINE
                    ? station.position.getX() < 0 ? Direction.WEST : Direction.EAST
                    : station.position.getX() < -2 ? Direction.EAST
                    : station.position.getX() > 2 ? Direction.WEST : Direction.NORTH;
            level.setBlock(station.position, block(station.task).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), Block.UPDATE_CLIENTS);
        }
        installRoomPartition(level);
        exposeEngineControls(level);
        level.setBlock(new BlockPos(-5, 78, 12), ScifiBlocks.SUPPORT_BEAM.defaultBlockState(), Block.UPDATE_CLIENTS);
        installWindows(level, blocks);
        Files.writeString(marker, "complete");
        NimbusNovaMod.LOGGER.info("Installed Return Window from team schematic: {} source blocks, {} equipment anchors", blocks.size(), MARKERS.size());
    }

    /** Positions come only from the team's original solid outer shell, never task or breach cells. */
    public static List<BlockPos> windowPositions(final MinecraftServer server) throws IOException {
        return windows(read(server));
    }

    private static List<BlockPos> windows(final List<Placed> original) {
        final Map<BlockPos, BlockState> source = new HashMap<>();
        for (final Placed placed : original) source.put(placed.position, placed.state);
        final Set<BlockPos> result = new HashSet<>();
        for (final Placed placed : original) {
            if (!placed.state.is(Blocks.CONCRETE.gray())) continue;
            final BlockPos position = placed.position;
            if (position.getY() == 77 && position.getZ() == -12 && (position.getX() == 11 || position.getX() == 12)) continue;
            final int localX = position.getX() + 13, localY = position.getY() - 64, localZ = position.getZ() + 41;
            final boolean bow = localY >= 17 && localY <= 18 && localZ >= 70 && localZ <= 75 && localX >= 5 && localX <= 21;
            final boolean lowerPort = localY >= 13 && localY <= 14
                    && (localX == 1 && localZ >= 20 && localZ <= 24 || localX == 25 && localZ >= 32 && localZ <= 35);
            final boolean upperPort = localY >= 18 && localY <= 19
                    && (localX == 1 && localZ >= 53 && localZ <= 56 || localX == 25 && localZ >= 45 && localZ <= 50);
            if (!bow && !lowerPort && !upperPort) continue;
            final Direction inward = bow ? Direction.NORTH : localX == 1 ? Direction.EAST : Direction.WEST;
            if (!source.getOrDefault(position.relative(inward.getOpposite()), Blocks.AIR.defaultBlockState()).isAir()) continue;
            final BlockPos liner = position.relative(inward);
            final BlockState lining = source.getOrDefault(liner, Blocks.AIR.defaultBlockState());
            if (bow) {
                if (lining.isAir() || lining.is(Blocks.LIGHT)) result.add(position);
                continue;
            }
            if (!lining.isAir() && !lining.is(Blocks.CONCRETE.gray()) && !lining.is(Blocks.CONCRETE.white())
                    && !lining.is(Blocks.DYED_TERRACOTTA.green())) continue;
            final BlockState interior = source.getOrDefault(position.relative(inward, 2), Blocks.AIR.defaultBlockState());
            if (!interior.isAir() && !interior.is(Blocks.LIGHT)) continue;
            result.add(position);
            if (!lining.isAir()) result.add(liner);
        }
        return List.copyOf(result);
    }

    private static void installWindows(final ServerLevel level, final List<Placed> original) {
        final Map<BlockPos, BlockState> source = new HashMap<>();
        for (final Placed placed : original) source.put(placed.position, placed.state);
        int installed = 0;
        for (final BlockPos position : windows(original)) {
            final BlockState current = level.getBlockState(position);
            if (!current.equals(source.get(position)) && !current.is(ScifiBlocks.HULL_PANEL)) continue;
            level.setBlock(position, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
            installed++;
        }
        NimbusNovaMod.LOGGER.info("Ship observation windows fitted: {} cells; modified cells were preserved", installed);
    }

    private static void installRoomPartition(final ServerLevel level) throws IOException {
        final Map<BlockPos, BlockState> original = new HashMap<>();
        for (final Placed placed : read(level.getServer())) original.put(placed.position, placed.state);
        for (final BlockPos position : BlockPos.betweenClosed(-11, 76, 8, -4, 79, 8)) {
            final BlockState current = level.getBlockState(position);
            if (!current.isAir() && !current.is(Blocks.CONCRETE.gray()) && !current.is(ScifiBlocks.HULL_PANEL)
                    && !current.is(ScifiBlocks.AIRLOCK_DOOR) && !current.equals(original.get(position))) {
                NimbusNovaMod.LOGGER.info("Room partition preserved occupied position {}", position);
                return;
            }
        }
        for (final BlockPos position : BlockPos.betweenClosed(-11, 76, 8, -4, 79, 8))
            level.setBlock(position, Blocks.CONCRETE.gray().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (int leaf = 0; leaf < 2; leaf++) {
            final BlockPos foot = new BlockPos(-8 + leaf, 76, 8);
            final var door = ScifiBlocks.AIRLOCK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                    .setValue(DoorBlock.HINGE, leaf == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
            level.setBlock(foot, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(foot.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.scheduleTick(foot, ScifiBlocks.AIRLOCK_DOOR, 5);
        }
    }

    private static void exposeEngineControls(final ServerLevel level) {
        for (final int x : new int[] { -9, 9 }) {
            final BlockPos cover = new BlockPos(x, 82, -38);
            final BlockState state = level.getBlockState(cover);
            if (state.getBlock() instanceof TrapDoorBlock)
                level.setBlock(cover, state.setValue(BlockStateProperties.OPEN, false), Block.UPDATE_CLIENTS);
        }
        for (final Marker marker : MARKERS) {
            if (marker.task != ShipTask.ENGINE) continue;
            final BlockState state = level.getBlockState(marker.position);
            if (state.is(ShipContent.ENGINE_CORE))
                level.setBlock(marker.position, state.setValue(HorizontalDirectionalBlock.FACING,
                        marker.position.getX() < 0 ? Direction.WEST : Direction.EAST), Block.UPDATE_CLIENTS);
        }
    }

    private static void installPanel(final ServerLevel level, final Set<BlockPos> cells) {
        final int minX = cells.stream().mapToInt(BlockPos::getX).min().orElseThrow(), maxX = cells.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        final int minY = cells.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        final int minZ = cells.stream().mapToInt(BlockPos::getZ).min().orElseThrow(), maxZ = cells.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        final Direction facing = minX == maxX ? Direction.EAST : Direction.NORTH;
        final int width = facing == Direction.EAST ? maxZ - minZ + 1 : maxX - minX + 1;
        for (final BlockPos cell : cells)
            level.setBlock(cell, ScifiBlocks.HULL_PANEL.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int leaf = 0; leaf < Math.min(2, width); leaf++) {
            final BlockPos foot = new BlockPos(minX + (facing == Direction.NORTH ? leaf : 0), minY, minZ + (facing == Direction.EAST ? leaf : 0));
            final BlockState state = ScifiBlocks.AIRLOCK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HINGE, leaf == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
            level.setBlock(foot, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(foot.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.scheduleTick(foot, ScifiBlocks.AIRLOCK_DOOR, 5);
        }
    }

    private static List<Placed> read(final MinecraftServer server) throws IOException {
        final var resource = ShipMap.class.getResourceAsStream("/data/nimbus_nova/ship/return_window.nvs.gz");
        if (resource == null) throw new IOException("The bundled spaceship schematic is missing.");
        try (final DataInputStream input = new DataInputStream(new GZIPInputStream(resource))) {
            if (input.readInt() != 0x4e565331) throw new IOException("Invalid ship data header.");
            final int width = input.readUnsignedShort(), height = input.readUnsignedShort(), length = input.readUnsignedShort();
            if (width != 26 || height != 31 || length != 82) throw new IOException("Unexpected ship dimensions.");
            final int paletteSize = input.readUnsignedShort();
            if (paletteSize < 1 || paletteSize > 4096) throw new IOException("Invalid ship palette.");
            final List<BlockState> palette = new ArrayList<>();
            for (int index = 0; index < paletteSize; index++) {
                final String original = input.readUTF();
                if (original.length() > 512) throw new IOException("Invalid ship block state.");
                palette.add(parse(server, original));
            }
            final int count = input.readInt(), volume = width * height * length;
            if (count < 1 || count > volume) throw new IOException("Invalid ship block count.");
            final boolean[] used = new boolean[volume];
            final List<Placed> result = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                final int location = input.readInt(), material = input.readUnsignedShort();
                if (location < 0 || location >= volume || material >= paletteSize || used[location]) throw new IOException("Invalid or duplicate ship block.");
                used[location] = true;
                result.add(new Placed(new BlockPos(location % width - 13, location / (width * length) + 64, location / width % length - 41), palette.get(material)));
            }
            if (input.read() != -1) throw new IOException("Unexpected trailing ship data.");
            return result;
        }
    }

    private static BlockState parse(final MinecraftServer server, final String original) throws IOException {
        final String name = original.substring(0, original.contains("[") ? original.indexOf('[') : original.length());
        if (name.endsWith("_sign") || name.endsWith("command_block")) return Blocks.AIR.defaultBlockState();
        try {
            return BlockStateParser.parseForBlock(server.registryAccess().lookupOrThrow(Registries.BLOCK),
                    original, false).blockState();
        } catch (final CommandSyntaxException error) {
            final String fallback = FALLBACKS.get(name.replace("minecraft:", ""));
            if (fallback == null) throw new IOException("Unsupported schematic material " + original, error);
            try {
                return BlockStateParser.parseForBlock(server.registryAccess().lookupOrThrow(Registries.BLOCK),
                        original.replace(name, "minecraft:" + fallback), false).blockState();
            } catch (final CommandSyntaxException fallbackError) {
                throw new IOException("Cannot preserve schematic shape for " + original, fallbackError);
            }
        }
    }

    public record Marker(BlockPos position, ShipTask task) {
        /* w */
    }

    private record Placed(BlockPos position, BlockState state) {
        /* w */
    }
}
