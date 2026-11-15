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
import net.minecraft.world.level.block.CropBlock;
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
    private static final int SITE = 44;
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
        final Path legacy = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/scenario-" + id + "-v1.txt");
        final Path marker = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/scenario-" + id + "-v2.txt");
        if (Files.exists(marker)) {
            if (!Files.readString(marker).equals("complete"))
                throw new IOException("This scenario build was interrupted. Existing blocks were preserved; use a backup or fresh test world.");
            return;
        }

        final Map<BlockPos, BlockState> plan = plan(id, false);
        final BlockPos spawn = BlockPos.containing(joinpos(id));
        if (plan.get(spawn.below()).isAir() || plan.containsKey(spawn) || plan.containsKey(spawn.above()))
            throw new IOException("Scenario spawn does not have a clear standing space.");
        for (final BlockPos station : STATIONS) {
            if (plan.get(station.below()).isAir() || !plan.containsKey(station))
                throw new IOException("Scenario station lost its floor or equipment: " + station);
        }

        // A finished first-generation site is replaced block by block; anything the crew added since stays where it is.
        final boolean upgrade = Files.exists(legacy) && Files.readString(legacy).equals("complete");
        if (upgrade) {
            for (final var placement : plan(id, true).entrySet()) {
                if (level.getBlockState(placement.getKey()).is(placement.getValue().getBlock()))
                    level.setBlock(placement.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        } else {
            for (final BlockPos position : plan.keySet())
                if (!level.getBlockState(position).isAir())
                    throw new IOException("Scenario creation stopped at " + position.toShortString() + ". Existing blocks were preserved.");
        }

        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "building", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        for (final var placement : plan.entrySet()) {
            if (upgrade && !level.getBlockState(placement.getKey()).isAir()) continue;
            level.setBlock(placement.getKey(), placement.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (final var placement : plan.entrySet()) {
            if (placement.getValue().is(ScifiBlocks.AIRLOCK_DOOR)
                    && placement.getValue().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                level.scheduleTick(placement.getKey(), ScifiBlocks.AIRLOCK_DOOR, 5);
        }
        Files.writeString(marker, "complete");
    }

    private static Map<BlockPos, BlockState> plan(final String id, final boolean legacy) {
        final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        if (legacy) {
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
        } else
            terrain(plan, id);

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
        if (!legacy)
            scenery(plan, id);

        return plan;
    }

    private static int hash(final int x, final int z, final int salt) {
        int value = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        value ^= value >>> 13;
        value *= 0x5bd1e995;
        return (value ^ value >>> 15) & 0x7fffffff;
    }

    private static int[][] craters(final String id) {
        return switch (id) {
            case "shelter" -> new int[][] { { -17, 8, 4 }, { 17, 18, 4 }, { -18, -19, 3 } };
            case "long_night" -> new int[][] { { -18, 17, 4 }, { -19, -15, 3 }, { 8, -16, 3 } };
            default -> new int[][] { { 18, 17, 4 }, { -18, 17, 4 }, { 0, -17, 4 } };
        };
    }

    /** A flat working plain ringed by rolling ground and a mountain rim that hides the edge of the site. */
    private static void terrain(final Map<BlockPos, BlockState> plan, final String id) {
        final Block[] palette = switch (id) {
            case "greenhouse" -> new Block[] { Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.orange() };
            case "long_night" -> new Block[] { Blocks.TUFF, Blocks.TUFF, Blocks.ANDESITE, Blocks.STONE, Blocks.CLAY };
            default -> new Block[] { Blocks.END_STONE, Blocks.END_STONE, Blocks.END_STONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE };
        };
        final Block floor = id.equals("long_night") ? Blocks.BLUE_ICE : id.equals("greenhouse") ? Blocks.DYED_TERRACOTTA.brown() : Blocks.END_STONE_BRICKS;
        final Block rock = id.equals("long_night") ? Blocks.COBBLED_DEEPSLATE : id.equals("greenhouse") ? Blocks.GRANITE : Blocks.END_STONE_BRICKS;
        for (int x = -SITE; x <= SITE; x++) {
            for (int z = -SITE; z <= SITE; z++) {
                final int outside = Math.max(Math.max(Math.abs(x), Math.abs(z)) - 24, 0);
                final double radius = Math.hypot(x, z);
                double relief = outside == 0 ? 0 : (Math.sin(x * 0.31) + Math.cos(z * 0.27) + Math.sin((x + z) * 0.11) * 1.6 - 1) * Math.min(1, outside / 5.0);
                if (radius > 30)
                    relief += Math.pow(radius - 30, 1.5) * 0.4 + Math.sin(x * 0.7) * Math.cos(z * 0.6) * Math.min(3, (radius - 30) * 0.4);

                Block surface = palette[hash(x, z, 1) % palette.length];
                for (final int[] crater : craters(id)) {
                    final double distance = Math.hypot(x - crater[0], z - crater[1]);
                    if (distance < crater[2]) {
                        relief -= 1 + (1 - distance / crater[2]) * 1.6;
                        surface = distance < crater[2] - 1.5 ? floor : surface;
                    } else if (distance < crater[2] + 1.3)
                        relief += 1;
                }

                final int height = Math.clamp(64 + (int) Math.round(relief), 58, 90);
                fill(plan, x, Math.max(56, height - 4), z, x, height - 1, z, Blocks.STONE);
                put(plan, x, height, z, surface);
                if (outside > 1 && hash(x, z, 7) % 61 == 0) {
                    put(plan, x, height + 1, z, rock);
                    if (hash(x, z, 9) % 3 == 0)
                        put(plan, x, height + 2, z, rock);
                }
            }
        }
    }

    private static void scenery(final Map<BlockPos, BlockState> plan, final String id) {
        fill(plan, -5, 64, 13, 5, 64, 23, Blocks.POLISHED_DEEPSLATE);
        for (int z = 13; z <= 23; z++) {
            put(plan, -5, 64, z, z % 2 == 0 ? Blocks.CONCRETE.yellow() : Blocks.CONCRETE.black());
            put(plan, 5, 64, z, z % 2 == 0 ? Blocks.CONCRETE.yellow() : Blocks.CONCRETE.black());
        }
        for (final int x : new int[] { -5, 5 }) {
            for (final int z : new int[] { 13, 23 })
                put(plan, x, 64, z, ScifiBlocks.FLOOR_LIGHT);
        }

        for (final int x : new int[] { -2, 2 }) {
            for (final int z : new int[] { 16, 21 })
                put(plan, x, 65, z, ScifiBlocks.SUPPORT_BEAM);
        }
        fill(plan, -2, 66, 15, 2, 66, 22, ScifiBlocks.REINFORCED_HULL);
        fill(plan, -2, 67, 15, 2, 68, 22, ScifiBlocks.HULL_PANEL);
        fill(plan, -1, 67, 16, 1, 68, 21, Blocks.AIR);
        fill(plan, -1, 69, 16, 1, 69, 21, ScifiBlocks.REINFORCED_HULL);
        fill(plan, -1, 67, 15, 1, 68, 15, ScifiBlocks.DOME_GLASS);
        for (final int x : new int[] { -2, 2 })
            fill(plan, x, 68, 17, x, 68, 20, ScifiBlocks.DOME_GLASS);
        fill(plan, -1, 66, 14, 1, 66, 14, ScifiBlocks.HULL_PANEL);
        put(plan, 0, 67, 14, ScifiBlocks.FLOOR_LIGHT);
        for (final int x : new int[] { -1, 1 }) {
            put(plan, x, 67, 23, ScifiBlocks.VENT);
            put(plan, x, 66, 23, Blocks.MAGMA_BLOCK);
        }
        put(plan, 0, 70, 18, ScifiBlocks.STRIP_LIGHT);
        put(plan, 0, 70, 21, ScifiBlocks.BEACON_MAST);
        put(plan, -4, 65, 14, ScifiBlocks.SUPPLY_CRATE);
        put(plan, 4, 65, 22, ScifiBlocks.SUPPLY_CRATE);

        if (id.equals("shelter")) {
            for (int step = 1; step <= 3; step++) {
                fill(plan, -9 - step, 65, -15 - step, 9 + step, 68 - step, -15 - step, Blocks.END_STONE);
                for (final int x : new int[] { -9 - step, 9 + step })
                    fill(plan, x, 65, -15 - step, x, 68 - step, -7, Blocks.END_STONE);
            }
            for (int y = 65; y <= 67; y++) {
                put(plan, -2, y, -6, y % 2 == 0 ? Blocks.CONCRETE.black() : Blocks.CONCRETE.yellow());
                put(plan, 1, y, -6, y % 2 == 0 ? Blocks.CONCRETE.yellow() : Blocks.CONCRETE.black());
            }
            fill(plan, -1, 67, -6, 0, 67, -6, Blocks.CONCRETE.yellow());
            put(plan, -4, 67, -6, ScifiBlocks.FLOOR_LIGHT);
            put(plan, 3, 67, -6, ScifiBlocks.FLOOR_LIGHT);
            for (final int x : new int[] { -7, 7 }) {
                put(plan, x, 65, -14, Blocks.BARREL);
                put(plan, x, 65, -13, ScifiBlocks.AIR_TANK);
            }
            solar(plan, 12, 6);
            solar(plan, 12, 10);
            mast(plan, 15, -3);
        } else if (id.equals("long_night")) {
            for (int z = -10; z <= -4; z += 2)
                fill(plan, 17, 66, z, 21, 66, z, ScifiBlocks.SOLAR_PANEL);
            for (final int x : new int[] { 15, 21 })
                put(plan, x, 71, 13, Blocks.END_ROD);
            put(plan, 19, 71, 13, ScifiBlocks.SATELLITE_DISH);
            for (final int x : new int[] { -4, 4 })
                put(plan, x, 69, -2, ScifiBlocks.VENT);
            put(plan, 9, 65, 7, Blocks.BARREL);
            put(plan, 9, 66, 7, Blocks.BARREL);
            put(plan, 8, 65, 7, Blocks.BARREL);
            put(plan, 3, 65, 6, Blocks.SMOOTH_STONE_SLAB);
            put(plan, 4, 65, 6, Blocks.SMOOTH_STONE_SLAB);
            for (int z = 12; z <= 22; z += 5) {
                put(plan, -8, 65, z, ScifiBlocks.SUPPORT_BEAM);
                put(plan, -8, 66, z, ScifiBlocks.FLOOR_LIGHT);
            }
            mast(plan, -14, 4);
        } else {
            fill(plan, -11, 71, -7, 11, 71, 8, Blocks.AIR);
            for (int x = -12; x <= 12; x++) {
                final int arch = 71 + (int) Math.round(Math.sqrt(Math.max(0, 1 - x * x / 169.0)) * 5);
                for (int z = -8; z <= 9; z++)
                    put(plan, x, arch, z, (z + 8) % 5 == 0 ? Blocks.IRON_BLOCK : Blocks.GLASS);
                for (final int z : new int[] { -8, 9 })
                    fill(plan, x, 71, z, x, arch, z, Blocks.GLASS);
                if (Math.abs(x) == 12)
                    fill(plan, x, 71, -8, x, arch, 9, Blocks.GLASS);
            }
            final Block[] crops = { Blocks.CARROTS, Blocks.WHEAT, Blocks.POTATOES };
            for (int bed = 0; bed < 3; bed++) {
                final int center = bed * 7 - 7;
                for (final BlockPos position : BlockPos.betweenClosed(center - 1, 65, -6, center + 1, 65, -3))
                    plan.put(position.immutable(), crops[bed].defaultBlockState().setValue(CropBlock.AGE, 7));
                fill(plan, center, 64, -5, center, 64, -4, Blocks.WATER);
                fill(plan, center, 65, -5, center, 65, -4, Blocks.AIR);
                fill(plan, center - 1, 71, -6, center + 1, 71, -3, Blocks.IRON_BARS);
                fill(plan, center, 72, -5, center, 70 + (int) Math.round(Math.sqrt(1 - center * center / 169.0) * 5), -5, Blocks.IRON_CHAIN);
            }
            fill(plan, -10, 65, 8, -8, 65, 8, Blocks.BARREL);
            put(plan, -9, 66, 8, Blocks.BARREL);
            fill(plan, 8, 65, 8, 10, 65, 8, ScifiBlocks.HYDROPONIC_TRAY);
            solar(plan, 16, 2);
            solar(plan, 16, 6);
            mast(plan, -16, -3);
        }
    }

    private static void solar(final Map<BlockPos, BlockState> plan, final int x, final int z) {
        fill(plan, x, 64, z - 1, x + 4, 64, z + 1, Blocks.POLISHED_DEEPSLATE);
        fill(plan, x, 65, z - 1, x + 4, 65, z + 1, ScifiBlocks.SOLAR_PANEL);
    }

    private static void mast(final Map<BlockPos, BlockState> plan, final int x, final int z) {
        fill(plan, x - 1, 64, z - 1, x + 1, 64, z + 1, Blocks.POLISHED_DEEPSLATE);
        fill(plan, x, 65, z, x, 67, z, ScifiBlocks.SUPPORT_BEAM);
        fill(plan, x, 68, z, x, 75, z, Blocks.IRON_BARS);
        put(plan, x, 76, z, ScifiBlocks.BEACON_MAST);
        put(plan, x + 1, 64, z + 2, Blocks.POLISHED_DEEPSLATE);
        put(plan, x + 1, 65, z + 2, ScifiBlocks.SATELLITE_DISH);
        put(plan, x - 1, 65, z + 1, ScifiBlocks.SUPPLY_CRATE);
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
        if (Math.abs(x) > SITE || Math.abs(z) > SITE || y < 56 || y > 96)
            throw new IllegalArgumentException("Scenario structure exceeds its reserved site");
        plan.put(new BlockPos(x, y, z), block.defaultBlockState());
    }
}
