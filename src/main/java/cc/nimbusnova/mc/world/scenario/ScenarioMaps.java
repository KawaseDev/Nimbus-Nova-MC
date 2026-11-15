package cc.nimbusnova.mc.world.scenario;

import cc.nimbusnova.mc.content.ScifiBlocks;
import cc.nimbusnova.mc.content.ShipContent;
import cc.nimbusnova.mc.scenario.ScenarioContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact authored training sites; landscape and shelter dimensions are game design, not surveyed terrain. */
public final class ScenarioMaps {
    private static final List<BlockPos> STATIONS = List.of(new BlockPos(-6, 65, 0), new BlockPos(0, 65, 0),
            new BlockPos(6, 65, 0), new BlockPos(-6, 65, 6));
    private static final List<BlockPos> SHIELD_SLOTS = List.of(new BlockPos(-2, 65, -8), new BlockPos(0, 65, -8), new BlockPos(2, 65, -8));

    private ScenarioMaps() {
        /* w */
    }

    public static ResourceKey<Level> dimension(final String id) {
        if (!List.of("shelter", "long_night", "greenhouse").contains(id))
            throw new IllegalArgumentException("Unknown outpost scenario: " + id);
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse("nimbus_nova:scenario_" + id));
    }

    public static Vec3 joinpos(final String id) {
        dimension(id);
        return new Vec3(0.5, 65, 10.5);
    }

    public static BlockPos station(final int index) {
        return STATIONS.get(index);
    }

    public static List<BlockPos> stationPositions(final String id) {
        dimension(id);
        return STATIONS;
    }

    public static List<BlockPos> shieldSlots() {
        return SHIELD_SLOTS;
    }

    public static void create(final MinecraftServer server, final String id) throws IOException {
        final ServerLevel level = server.getLevel(dimension(id));
        if (level == null) throw new IOException("Scenario dimension is unavailable. Restart with the updated mod.");
        final Path marker = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/scenario-" + id + "-v1.txt");
        if (Files.exists(marker)) {
            if (!Files.readString(marker).equals("complete"))
                throw new IOException("This scenario build was interrupted. Existing blocks were preserved; use a backup or fresh test world.");
            return;
        }

        final Map<BlockPos, BlockState> plan = plan(id);
        final BlockPos spawn = BlockPos.containing(joinpos(id));
        if (plan.get(spawn.below()).isAir() || plan.containsKey(spawn) || plan.containsKey(spawn.above()))
            throw new IOException("Scenario spawn does not have a clear standing space.");
        for (final BlockPos station : STATIONS) {
            if (plan.get(station.below()).isAir() || !plan.containsKey(station))
                throw new IOException("Scenario station lost its floor or equipment: " + station);
        }
        for (final BlockPos position : plan.keySet())
            if (!level.getBlockState(position).isAir())
                throw new IOException("Scenario creation stopped at " + position.toShortString() + ". Existing blocks were preserved.");

        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "building", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        for (final var placement : plan.entrySet())
            level.setBlock(placement.getKey(), placement.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (final var placement : plan.entrySet()) {
            if (placement.getValue().is(ScifiBlocks.AIRLOCK_DOOR)
                    && placement.getValue().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                level.scheduleTick(placement.getKey(), ScifiBlocks.AIRLOCK_DOOR, 5);
        }
        Files.writeString(marker, "complete");
    }

    private static Map<BlockPos, BlockState> plan(final String id) {
        final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        final Block surface = id.equals("greenhouse") ? Blocks.RED_SANDSTONE : id.equals("long_night") ? Blocks.SNOW_BLOCK : Blocks.END_STONE;
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                final double crater = Math.hypot(x - 18, z + 15);
                final int height = Math.abs(x) <= 14 && Math.abs(z) <= 16 ? 64
                        : Math.clamp(62 + (int) Math.round(Math.sin(x * 0.31) + Math.cos(z * 0.27)) - (crater < 5 ? 3 : 0), 59, 64);
                fill(plan, x, 57, z, x, height - 1, z, Blocks.STONE);
                fill(plan, x, height, z, x, height, z, surface);
            }
        }
        if (id.equals("shelter"))
            shelter(plan);
        else if (id.equals("long_night"))
            night(plan);
        else
            greenhouse(plan);

        for (int z = -5; z <= 12; z++) {
            for (int x = -1; x <= 1; x++)
                put(plan, x, 64, z, Blocks.SMOOTH_STONE);
        }
        for (int x = -10; x <= 10; x++)
            put(plan, x, 64, 2, Blocks.SMOOTH_STONE);
        for (final int x : new int[] { -9, 9 }) {
            for (final int z : new int[] { 2, 8 })
                put(plan, x, 64, z, ScifiBlocks.FLOOR_LIGHT);
        }
        final Block[] stations = switch (id) {
            case "shelter" -> new Block[] { ShipContent.SCIENCE_LAB, ShipContent.MEETING_TERMINAL, ShipContent.COMMS_ARRAY };
            case "long_night" -> new Block[] { ShipContent.ENGINE_CORE, ShipContent.SCIENCE_LAB, ShipContent.COMMS_ARRAY };
            default -> new Block[] { ShipContent.HYDROPONICS, ShipContent.MEDICAL_STATION, ShipContent.MEETING_TERMINAL };
        };
        for (int index = 0; index < 3; index++)
            plan.put(STATIONS.get(index), stations[index].defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        plan.put(STATIONS.get(3), ShipContent.STORAGE_LOCKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        return plan;
    }

    private static void shelter(final Map<BlockPos, BlockState> plan) {
        room(plan, -8, -15, 8, -6, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, 68);
        fill(plan, -8, 69, -15, 8, 70, -6, Blocks.END_STONE);
        fill(plan, -6, 71, -14, 6, 72, -7, Blocks.END_STONE);
        fill(plan, -4, 73, -12, 4, 73, -9, Blocks.END_STONE);
        for (final int x : new int[] { -9, 9 })
            fill(plan, x, 65, -15, x, 67, -6, Blocks.END_STONE);
        fill(plan, -5, 65, -9, 5, 67, -9, Blocks.POLISHED_DEEPSLATE);
        fill(plan, -5, 65, -8, 5, 67, -8, Blocks.POLISHED_DEEPSLATE);
        for (final BlockPos slot : SHIELD_SLOTS)
            plan.put(slot, Blocks.AIR.defaultBlockState());
        put(plan, -6, 67, -11, ScifiBlocks.FLOOR_LIGHT);
        put(plan, 6, 67, -11, ScifiBlocks.FLOOR_LIGHT);
        door(plan, 0, -6, Direction.SOUTH);
        for (final int z : new int[] { -7, -10, -12 })
            put(plan, -6, 64, z, ScifiBlocks.FLOOR_LIGHT);
        fill(plan, -6, 64, -12, 2, 64, -12, Blocks.CONCRETE.yellow());
        fill(plan, -7, 64, -3, 7, 64, 7, Blocks.SMOOTH_STONE);
        for (final int x : new int[] { -12, 12 }) {
            fill(plan, x, 65, -2, x, 67, -2, Blocks.IRON_BLOCK);
            put(plan, x, 68, -2, ScifiBlocks.FLOOR_LIGHT);
        }
        for (int z = -20; z <= -6; z += 3)
            put(plan, 18, 64, z, ScifiBlocks.FLOOR_LIGHT);
    }

    private static void night(final Map<BlockPos, BlockState> plan) {
        room(plan, -12, -6, 12, 9, Blocks.CONCRETE.lightGray(), Blocks.POLISHED_DEEPSLATE, 70);
        fill(plan, -12, 66, -3, -12, 68, 5, Blocks.GLASS);
        fill(plan, 12, 66, -3, 12, 68, 5, Blocks.GLASS);
        fill(plan, -8, 66, -6, -3, 68, -6, Blocks.GLASS);
        fill(plan, 3, 66, -6, 8, 68, -6, Blocks.GLASS);
        for (final int x : new int[] { -8, 0, 8 })
            put(plan, x, 69, 2, ScifiBlocks.FLOOR_LIGHT);
        for (final int x : new int[] { -11, 11 }) {
            fill(plan, x, 65, -5, x, 65, 7, ScifiBlocks.CABLE_TRUNK);
            for (final int z : new int[] { -4, 0, 4 })
                put(plan, x, 66, z, ScifiBlocks.BATTERY_BANK);
        }
        door(plan, 0, 9, Direction.SOUTH);
        fill(plan, 16, 64, -11, 22, 64, -3, Blocks.POLISHED_DEEPSLATE);
        for (int z = -10; z <= -4; z += 2) {
            fill(plan, 17, 65, z, 21, 65, z, Blocks.CONCRETE.blue());
            put(plan, 19, 66, z, ScifiBlocks.STRIP_LIGHT);
        }
        fill(plan, 18, 65, 13, 18, 71, 13, Blocks.IRON_BLOCK);
        fill(plan, 15, 70, 13, 21, 70, 13, Blocks.IRON_BLOCK);
        put(plan, 18, 72, 13, ScifiBlocks.FLOOR_LIGHT);
        for (int x = 3; x <= 18; x++)
            put(plan, x, 64, 13, Blocks.POLISHED_DEEPSLATE);
        for (final int z : new int[] { -21, 20 })
            fill(plan, -22, 63, z, 22, 64, z, Blocks.PACKED_ICE);
    }

    private static void greenhouse(final Map<BlockPos, BlockState> plan) {
        room(plan, -12, -8, 12, 9, Blocks.GLASS, Blocks.GLASS, 71);
        for (int x = -12; x <= 12; x += 6)
            fill(plan, x, 71, -8, x, 71, 9, Blocks.IRON_BLOCK);
        for (final int x : new int[] { -12, 12 }) {
            for (int z = -8; z <= 9; z += 5)
                fill(plan, x, 65, z, x, 71, z, Blocks.IRON_BLOCK);
        }
        for (final int center : new int[] { -7, 0, 7 }) {
            fill(plan, center - 2, 64, -7, center + 2, 64, -2, Blocks.SMOOTH_STONE);
            fill(plan, center - 1, 64, -6, center + 1, 64, -3, Blocks.FARMLAND);
            fill(plan, center - 1, 65, -6, center + 1, 65, -3, Blocks.WHEAT);
            fill(plan, center - 2, 65, -7, center + 2, 65, -7, ScifiBlocks.HYDROPONIC_TRAY);
            fill(plan, center - 1, 70, -6, center + 1, 70, -3, ScenarioContent.GROW_LIGHT);
        }
        fill(plan, -10, 64, -1, 10, 64, -1, Blocks.STAINED_GLASS.blue());
        fill(plan, -11, 65, 3, -11, 67, 7, ScifiBlocks.AIR_TANK);
        fill(plan, 11, 65, 3, 11, 67, 7, ScifiBlocks.CRYO_CABINET);
        put(plan, -4, 65, 3, Blocks.COPPER_BLOCK.weathering().unaffected());
        door(plan, 0, 9, Direction.SOUTH);
        fill(plan, -3, 64, 12, 3, 64, 18, Blocks.SMOOTH_STONE);
        for (final int x : new int[] { -4, 4 })
            fill(plan, x, 65, 12, x, 67, 18, ScifiBlocks.SUPPORT_BEAM);
    }

    private static void room(final Map<BlockPos, BlockState> plan, final int minX, final int minZ, final int maxX,
                             final int maxZ, final Block wall, final Block roof, final int ceiling) {
        fill(plan, minX, 64, minZ, maxX, 64, maxZ, Blocks.SMOOTH_STONE);
        fill(plan, minX, 65, minZ, maxX, ceiling - 1, minZ, wall);
        fill(plan, minX, 65, maxZ, maxX, ceiling - 1, maxZ, wall);
        fill(plan, minX, 65, minZ, minX, ceiling - 1, maxZ, wall);
        fill(plan, maxX, 65, minZ, maxX, ceiling - 1, maxZ, wall);
        fill(plan, minX, ceiling, minZ, maxX, ceiling, maxZ, roof);
    }

    private static void door(final Map<BlockPos, BlockState> plan, final int x, final int z, final Direction facing) {
        for (int leaf = 0; leaf < 2; leaf++) {
            final BlockState state = ScifiBlocks.AIRLOCK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HINGE, (leaf == 0) != (facing == Direction.SOUTH) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
            final BlockPos foot = new BlockPos(x + leaf - 1, 65, z);
            plan.put(foot, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            plan.put(foot.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    private static void fill(final Map<BlockPos, BlockState> plan, final int minX, final int minY, final int minZ,
                             final int maxX, final int maxY, final int maxZ, final Block block) {
        for (final BlockPos position : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ))
            put(plan, position.getX(), position.getY(), position.getZ(), block);
    }

    private static void put(final Map<BlockPos, BlockState> plan, final int x, final int y, final int z, final Block block) {
        if (Math.abs(x) > 24 || Math.abs(z) > 24 || y < 57 || y > 80)
            throw new IllegalArgumentException("Scenario structure exceeds its reserved site");
        plan.put(new BlockPos(x, y, z), block.defaultBlockState());
    }
}
