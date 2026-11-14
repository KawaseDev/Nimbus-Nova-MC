package cc.nimbusnova.mc.world;

import cc.nimbusnova.mc.content.ModBlocks;
import cc.nimbusnova.mc.content.OutpostContent;
import cc.nimbusnova.mc.content.ScienceStations;
import cc.nimbusnova.mc.content.ScifiBlocks;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.npc.MissionNpcs;
import cc.nimbusnova.mc.vehicle.RoverContent;
import net.minecraft.world.entity.EntitySpawnReason;
import cc.nimbusnova.mc.mission.Station;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MarsTrainingMap {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.parse("nimbus_nova:mars_training"));
    public static final BlockPos RESEARCH_STATION = new BlockPos(-24, 66, 0);
    public static final BlockPos OXYGEN_STATION = new BlockPos(-5, 66, 3);
    public static final BlockPos SAMPLE_DEPOSIT = new BlockPos(40, 66, -16);
    public static final BlockPos SAMPLE_ANALYZER = new BlockPos(6, 66, 1);
    public static final BlockPos SUPPLY_LOCKER = new BlockPos(7, 66, -3);
    public static final BlockPos WEATHER_STATION = new BlockPos(14, 66, -4);
    private static final int FLOOR_Y = 65;
    private static final int SITE_RADIUS = 48;
    private static final Map<MinecraftServer, Map<UUID, ReturnPoint>> RETURN_POINTS = new IdentityHashMap<>();

    private MarsTrainingMap() {
        /* w */
    }

    public static void register() {
        MissionController.setShelterCheck(player -> player.level().dimension() != DIMENSION ? null : !inShelter(player.blockPosition()));
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.parse("nimbus_nova:mars_surface"), MarsTerrainGenerator.CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(RETURN_POINTS::remove);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            final ServerLevel level = server.getLevel(DIMENSION);
            if (level != null)
                migrateLaserDoors(level);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> dispatcher.register(Commands.literal("mars")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Mars training: /mars create, /mars upgrade, /mars dome (operator), /mars join, /mars leave. "
                            + MarsTerrain.terrainDescription()), false);
                    return 1;
                })
                .then(Commands.literal("create").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            try {
                                return create(context.getSource());
                            } catch (final IOException exception) {
                                context.getSource().sendFailure(Component.literal("Outpost state could not be saved/read. No retry will overwrite existing terrain: " + exception.getMessage()));
                                return 0;
                            }
                        }))
                .then(Commands.literal("upgrade").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            try {
                                return upgrade(context.getSource());
                            } catch (final IOException exception) {
                                context.getSource().sendFailure(Component.literal("Cannot verify the outpost state: " + exception.getMessage()));
                                return 0;
                            }
                        }))
                .then(Commands.literal("dome").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            try {
                                return addDome(context.getSource());
                            } catch (final IOException exception) {
                                context.getSource().sendFailure(Component.literal("Cannot verify the outpost state: " + exception.getMessage()));
                                return 0;
                            }
                        }))
                .then(Commands.literal("join").executes(context -> {
                    try {
                        return join(context.getSource().getPlayerOrException());
                    } catch (final IOException exception) {
                        context.getSource().sendFailure(Component.literal("Cannot read outpost state. Ask an operator to inspect the world data."));
                        return 0;
                    }
                }))
                .then(Commands.literal("leave").executes(context -> leave(context.getSource().getPlayerOrException())))));
    }

    public static int create(final CommandSourceStack source) throws IOException {
        final ServerLevel level = source.getServer().getLevel(DIMENSION);
        if (level == null) {
            source.sendFailure(Component.literal("The Mars dimension is unavailable. Restart the server after installing Nimbus Nova."));
            return 0;
        }

        final int constructionStage = readConstructionStage(source.getServer());
        if (constructionStage == 2) {
            bindStations(source.getServer());
            source.sendSuccess(() -> Component.literal("The Mars outpost already exists. It was preserved; use /mars join."), false);
            return 1;
        }

        if (constructionStage == 1) {
            source.sendFailure(Component.literal("A previous outpost build was interrupted. Existing blocks are preserved. Recover a backup or use a fresh test world."));
            return 0;
        }

        for (final BlockPos position : BlockPos.betweenClosed(-SITE_RADIUS, FLOOR_Y, -SITE_RADIUS,
                SITE_RADIUS, FLOOR_Y + OutpostDome.HEIGHT, SITE_RADIUS)) {
            if (level.getBlockState(position).isAir()) continue;
            source.sendFailure(Component.literal("The reserved Mars site contains existing blocks. Creation stopped without replacing them."));
            return 0;
        }

        // Claim the site on disk before placing blocks, so an interrupted build never overwrites later player work.
        writeConstructionStage(source.getServer(), false);
        source.sendSuccess(() -> Component.literal("Building the original Mars training outpost..."), false);

        OutpostDome.build(level);
        buildPaths(level);
        buildLandingPad(level);
        buildHabitat(level);
        buildPowerYard(level);
        buildGreenhouse(level);
        buildShelter(level);
        buildRoverBay(level);
        buildTerrainDetails(level);
        buildMissionEquipment(level);
        buildWayfinding(level);
        beacon(level, -8, -18, Blocks.CONCRETE.orange());
        beacon(level, -3, -13, Blocks.CONCRETE.cyan());
        verifyArrival(level);
        writeConstructionStage(source.getServer(), true);
        bindStations(source.getServer());
        source.sendSuccess(() -> Component.literal("Mars outpost ready. Students can use /mars join. Fictional training site / authored terrain."), false);
        return 1;
    }

    private static int join(final ServerPlayer player) throws IOException {
        final MinecraftServer server = player.level().getServer();
        final ServerLevel destination = server.getLevel(DIMENSION);
        if (destination == null || readConstructionStage(server) != 2) {
            player.sendSystemMessage(Component.literal("Ask an operator to prepare the outpost with /mars create first."));
            return 0;
        }

        migrateLaserDoors(destination);
        if (player.level().dimension() != DIMENSION) {
            RETURN_POINTS.computeIfAbsent(server, _ -> new HashMap<>()).put(player.getUUID(),
                    new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
        }

        verifyArrival(destination);
        bindStations(server);
        MissionNpcs.spawnAt(destination, -3.5, FLOOR_Y + 1, -3.5, false);
        MissionNpcs.spawnAt(destination, 4.5, FLOOR_Y + 1, 2.5, true);
        player.teleportTo(destination, 0.5, FLOOR_Y + 1, -27.5, Set.of(), 0, 0, true);
        player.resetFallDistance();
        player.sendSystemMessage(Component.literal("Welcome to Aster Outpost. Fictional training site / authored terrain. Press J for mission controls. /mars leave returns you."));
        if (!destination.getBlockState(SAMPLE_ANALYZER).is(OutpostContent.SAMPLE_ANALYZER))
            player.sendSystemMessage(Component.literal("This older outpost needs mission equipment. Ask Mission Control to run /mars upgrade."));

        return 1;
    }

    private static int upgrade(final CommandSourceStack source) throws IOException {
        final ServerLevel level = source.getServer().getLevel(DIMENSION);
        if (level == null || readConstructionStage(source.getServer()) != 2) {
            source.sendFailure(Component.literal("Create an outpost with /mars create before upgrading it."));
            return 0;
        }

        final var phase = MissionController.get(source.getServer()).snapshot().phase();
        if (phase == MissionState.Phase.ACTIVE || phase == MissionState.Phase.PAUSED) {
            source.sendFailure(Component.literal("End or reset the mission before upgrading the outpost."));
            return 0;
        }

        final DoorSite[] doors = outpostDoors();
        for (final DoorSite door : doors) {
            final BlockPos unsafe = unsafeDoorway(level, door);
            if (unsafe == null) continue;
            source.sendFailure(Component.literal("Upgrade stopped: a doorway or its approach was changed at " + unsafe
                    + ". Your blocks were preserved; clear that opening or use a fresh test world."));
            return 0;
        }

        final EquipmentSite[] equipment = {
                new EquipmentSite(SAMPLE_DEPOSIT, OutpostContent.SAMPLE_DEPOSIT, Blocks.COPPER_ORE),
                new EquipmentSite(SAMPLE_ANALYZER, OutpostContent.SAMPLE_ANALYZER, Blocks.AIR),
                new EquipmentSite(SUPPLY_LOCKER, OutpostContent.SUPPLY_LOCKER, Blocks.AIR),
                new EquipmentSite(WEATHER_STATION, OutpostContent.WEATHER_TERMINAL, Blocks.AIR),
                new EquipmentSite(new BlockPos(-1, 66, -10), ScienceStations.AIRLOCK_CONTROL, Blocks.AIR),
                new EquipmentSite(new BlockPos(3, 66, 5), ScienceStations.BACKUP_POWER, Blocks.AIR),
                new EquipmentSite(new BlockPos(2, 66, 25), ScienceStations.SHIELD_BENCH, Blocks.AIR)
        };
        for (final EquipmentSite site : equipment) {
            final var current = level.getBlockState(site.position);
            final var floor = level.getBlockState(site.position.below());
            final boolean floorCanBeAdded = floor.isAir()
                    && level.getBlockState(site.position.below(2)).isFaceSturdy(level, site.position.below(2), Direction.UP);
            if ((current.isAir() || current.is(site.replacement) || current.is(site.prototype))
                    && (floor.isFaceSturdy(level, site.position.below(), Direction.UP) || floorCanBeAdded)) continue;
            source.sendFailure(Component.literal("Upgrade stopped: the mission equipment spot was changed at " + site.position
                    + ". No blocks were replaced. Move your build away from that anchor or use a fresh test world."));
            return 0;
        }

        for (final DoorSite door : doors)
            installDoorway(level, door);

        for (final BlockPos position : BlockPos.betweenClosed(-8, FLOOR_Y + 1, 4, -3, FLOOR_Y + 1, 6)) {
            if (level.getBlockState(position).is(Blocks.SMOOTH_QUARTZ))
                level.setBlock(position, ScifiBlocks.HULL_PANEL.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        for (final EquipmentSite site : equipment) {
            if (level.getBlockState(site.position.below()).isAir())
                level.setBlock(site.position.below(), Blocks.CONCRETE.lightGray().defaultBlockState(), Block.UPDATE_CLIENTS);

            if (!level.getBlockState(site.position).is(site.replacement))
                level.setBlock(site.position, site.replacement.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        bindStations(source.getServer());
        final int removedSigns = removePrototypeSigns(level);
        upgradeProjector(level, -8, -18, Blocks.CONCRETE.orange());
        upgradeProjector(level, -3, -13, Blocks.CONCRETE.cyan());
        upgradeProjector(level, -4, 18, Blocks.CONCRETE.yellow());
        upgradeProjector(level, 20, 17, Blocks.CONCRETE.lightBlue());
        upgradeProjector(level, -18, -4, Blocks.CONCRETE.orange());
        upgradeProjector(level, 10, -17, Blocks.CONCRETE.cyan());
        source.sendSuccess(() -> Component.literal("Mission equipment and original doorways are ready. Replaced " + removedSigns
                + " known prototype signs with hologram guidance; other terrain and builds were preserved."), false);
        return 1;
    }

    private static int addDome(final CommandSourceStack source) throws IOException {
        final ServerLevel level = source.getServer().getLevel(DIMENSION);
        if (level == null || readConstructionStage(source.getServer()) != 2) {
            source.sendFailure(Component.literal("Create an outpost first. The dome command preserves existing builds."));
            return 0;
        }

        final var phase = MissionController.get(source.getServer()).snapshot().phase();
        if (phase == MissionState.Phase.ACTIVE || phase == MissionState.Phase.PAUSED) {
            source.sendFailure(Component.literal("End or reset the mission before adding the dome."));
            return 0;
        }

        final BlockPos conflict = OutpostDome.findConflict(level);
        if (conflict != null) {
            source.sendFailure(Component.literal("Dome installation stopped at " + conflict + ". Existing blocks were preserved. Clear the shell footprint or use a fresh test world."));
            return 0;
        }

        OutpostDome.build(level);
        source.sendSuccess(() -> Component.literal("Aster's demonstration dome is installed. Glass does not protect against radiation; use the reinforced crew shelter."), false);
        return 1;
    }

    private static void upgradeProjector(final ServerLevel level, final int x, final int z, final Block color) {
        final BlockPos position = new BlockPos(x, FLOOR_Y + 1, z);
        if (level.getBlockState(position).is(OutpostContent.HOLOGRAM_PROJECTOR)) return;

        final boolean originalBeacon = level.getBlockState(position.below()).is(color)
                && level.getBlockState(position).is(color) && level.getBlockState(position.above()).is(color)
                && level.getBlockState(position.above(2)).is(color) && level.getBlockState(position.above(3)).is(Blocks.OCHRE_FROGLIGHT)
                && level.getBlockState(position.above(4)).isAir();
        if (originalBeacon) {
            for (int height = 1; height <= 3; height++)
                level.setBlock(position.above(height), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        } else if (!level.getBlockState(position).isAir()) return;

        if (!level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP)) {
            if (!level.getBlockState(position.below()).isAir()
                    || !level.getBlockState(position.below(2)).isFaceSturdy(level, position.below(2), Direction.UP)) return;

            level.setBlock(position.below(), color.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        level.setBlock(position, OutpostContent.HOLOGRAM_PROJECTOR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static int removePrototypeSigns(final ServerLevel level) {
        final LegacySign[] signs = {
                new LegacySign(new BlockPos(-8, 66, -18), List.of("ASTER OUTPOST", "Landing pad", "Training terrain", "/mars leave")),
                new LegacySign(new BlockPos(-4, 67, 6), List.of("OXYGEN SYSTEM", "Right-click stone", "Life-support role", "Shared reserves")),
                new LegacySign(new BlockPos(4, 67, 6), List.of("MISSION CONTROL", "Press J", "Select a role", "Prepare the crew")),
                new LegacySign(new BlockPos(-4, 66, -6), List.of("ASTER HABITAT", "Fictional site", "Authored terrain", "No suit hazard yet")),
                new LegacySign(new BlockPos(-2, 67, -13), List.of("AIRLOCK ENTRY", "Walk onto the plate", "Doors open for you", "Pressure not modeled")),
                new LegacySign(new BlockPos(-20, 66, 0), List.of("POWER / RESEARCH", "Right-click console", "Power engineer", "Research uses power")),
                new LegacySign(new BlockPos(-20, 66, 0), List.of("POWER / RESEARCH", "Right-click console", "Repair before launch", "Power engineer")),
                new LegacySign(new BlockPos(-35, 66, 0), List.of("SOLAR ARRAY", "Dust lowers supply", "Panels are scenery", "J: live simulation")),
                new LegacySign(new BlockPos(21, 66, -3), List.of("GREENHOUSE", "Demonstration crops", "Food model planned", "Water is limited")),
                new LegacySign(new BlockPos(-4, 66, 18), List.of("CREW SHELTER", "Protection has cost", "Stock supplies first", "Radiation planned")),
                new LegacySign(new BlockPos(-4, 66, 18), List.of("CREW SHELTER", "Solar storm? Get in", "No consoles inside", "Plan before alerts")),
                new LegacySign(new BlockPos(20, 66, 17), List.of("ROVER WORKSHOP", "Static rover mockup", "Driving is planned", "Explore on foot")),
                new LegacySign(new BlockPos(20, 66, 17), List.of("ROVER WORKSHOP", "Right-click to board", "Movement keys drive", "Sneak to exit")),
                new LegacySign(new BlockPos(39, 66, -12), List.of("SCIENCE SITE", "Inspect rock layers", "Fictional samples", "Return to habitat")),
                new LegacySign(new BlockPos(39, 66, -12), List.of("SAMPLE SITE", "Right-click the rock", "Take it to analyzer", "Fictional sample")),
                new LegacySign(new BlockPos(5, 66, -12), List.of("OUTPOST ROUTES", "Habitat: ahead", "Power: left", "Greenhouse: right")),
                new LegacySign(new BlockPos(8, 66, -3), List.of("CREW SUPPLIES", "Right-click locker", "Take your mission kit", "One kit per astronaut")),
                new LegacySign(new BlockPos(8, 66, 1), List.of("SAMPLE ANALYZER", "Bring back the sample", "Right-click to deliver", "Then prepare launch")),
                new LegacySign(new BlockPos(13, 66, -2), List.of("WEATHER STATION", "Right-click to scan", "Tell the crew", "Forecast solar power")),
                new LegacySign(new BlockPos(-18, 67, -5), List.of("POWER", "Repair station", "Watch your objective", "Follow its marker")),
                new LegacySign(new BlockPos(10, 67, -18), List.of("SAMPLE ROUTE", "Follow blue arrows", "Watch your objective", "Follow its marker")),
                new LegacySign(new BlockPos(4, 67, 16), List.of("SHELTER", "Enter for solar alert", "Watch your objective", "Follow its marker")),
                new LegacySign(new BlockPos(19, 67, 15), List.of("ROVER BAY", "Right-click to drive", "Watch your objective", "Follow its marker"))
        };
        int removed = 0;
        for (final LegacySign legacy : signs) {
            final BlockState state = level.getBlockState(legacy.position);
            if ((!state.is(Blocks.OAK_SIGN) && !state.is(Blocks.OAK_WALL_SIGN))
                    || !(level.getBlockEntity(legacy.position) instanceof SignBlockEntity sign)) continue;

            final boolean originalFront = sign.getText(SignTextSlot.FRONT).getMessages(false).stream().map(Component::getString).toList().equals(legacy.lines);
            final boolean originalBack = sign.getText(SignTextSlot.BACK).getMessages(false).stream().map(Component::getString).toList().equals(legacy.lines);
            if (!originalFront || !originalBack) continue;

            level.setBlock(legacy.position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            removed++;
        }

        return removed;
    }

    private static boolean doorInstalled(final ServerLevel level, final DoorSite site) {
        for (int index = -1; index <= 0; index++) {
            final BlockPos foot = site.center.relative(site.facing.getClockWise(), index);
            final BlockState lower = level.getBlockState(foot), upper = level.getBlockState(foot.above());
            if (!(lower.getBlock() instanceof DoorBlock) || upper.getBlock() != lower.getBlock()
                    || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || upper.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER)
                return false;
        }

        return true;
    }

    private static BlockPos unsafeDoorway(final ServerLevel level, final DoorSite site) {
        if (doorInstalled(level, site)) return null;

        final Direction side = site.facing.getClockWise();
        for (int width = -1; width <= 1; width++) {
            for (int height = 0; height <= 2; height++) {
                final BlockPos position = site.center.relative(side, width).above(height);
                final var state = level.getBlockState(position);
                if (!state.isAir() && !state.is(site.wall)) return position;
            }
        }

        for (int index = -1; index <= 0; index++) {
            final BlockPos foot = site.center.relative(side, index);
            if (!level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP)) return foot.below();
            for (final Direction approach : new Direction[] { site.facing, site.facing.getOpposite() }) {
                final BlockPos plate = foot.relative(approach);
                final var state = level.getBlockState(plate);
                if ((!state.isAir() && !state.is(Blocks.STONE_PRESSURE_PLATE))
                        || !level.getBlockState(plate.below()).isFaceSturdy(level, plate.below(), Direction.UP)) return plate;
            }
        }

        return null;
    }

    private static int leave(final ServerPlayer player) {
        final MinecraftServer server = player.level().getServer();
        if (player.level().dimension() != DIMENSION) {
            player.sendSystemMessage(Component.literal("You are not at the Mars training site."));
            return 0;
        }

        final Map<UUID, ReturnPoint> points = RETURN_POINTS.get(server);
        ReturnPoint point = points == null ? null : points.get(player.getUUID());
        ServerLevel destination = point == null ? null : server.getLevel(point.dimension);
        if (destination == null || !safeFeet(destination, BlockPos.containing(point.position))) {
            destination = server.overworld();
            point = findSafeSpawn(destination);
        }

        if (point == null) {
            player.sendSystemMessage(Component.literal("No safe return spot was found near world spawn. Ask an operator to teleport you; no blocks were changed."));
            return 0;
        }

        if (player.teleportTo(destination, point.position.x, point.position.y, point.position.z,
                Set.of(), point.yaw, point.pitch, true)) {
            if (points != null)
                points.remove(player.getUUID());

            player.resetFallDistance();
            return 1;
        }

        return 0;
    }

    private static ReturnPoint findSafeSpawn(final ServerLevel level) {
        final BlockPos spawn = level.getRespawnData().pos();
        for (int radius = 0; radius <= 16; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) continue;
                    final int x = spawn.getX() + offsetX, z = spawn.getZ() + offsetZ;
                    final BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (safeFeet(level, feet))
                        return new ReturnPoint(level.dimension(), Vec3.atBottomCenterOf(feet), 0, 0);
                }
            }
        }

        return null;
    }

    private static boolean safeFeet(final ServerLevel level, final BlockPos feet) {
        final BlockPos floor = feet.below();
        return level.getWorldBorder().isWithinBounds(feet)
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && !level.getBlockState(floor).is(Blocks.MAGMA_BLOCK)
                && !level.getBlockState(floor).is(Blocks.CACTUS)
                && !level.getBlockState(floor).is(Blocks.CAMPFIRE)
                && !level.getBlockState(floor).is(Blocks.SOUL_CAMPFIRE)
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void bindStations(final MinecraftServer server) {
        final MissionController controller = MissionController.get(server);
        controller.bindStation(GlobalPos.of(DIMENSION, RESEARCH_STATION), Station.RESEARCH);
        controller.bindStation(GlobalPos.of(DIMENSION, OXYGEN_STATION), Station.OXYGEN);
    }

    private static void buildPaths(final ServerLevel level) {
        for (int x = -17; x <= 17; x++) {
            for (int z = -17; z <= 17; z++) {
                if (x * x + z * z > 17 * 17) continue;
                place(level, x, FLOOR_Y, z, ScifiBlocks.FLOOR_TILE);
            }
        }

        fill(level, -2, FLOOR_Y, -28, 2, FLOOR_Y, 30, ScifiBlocks.FLOOR_TILE);
        fill(level, -32, FLOOR_Y, -2, 32, FLOOR_Y, 2, ScifiBlocks.FLOOR_TILE);
        fill(level, 0, FLOOR_Y, 23, 30, FLOOR_Y, 27, ScifiBlocks.FLOOR_TILE);
        for (int distance = -24; distance <= 24; distance += 8) {
            place(level, -3, FLOOR_Y, distance, ScifiBlocks.FLOOR_LIGHT);
            place(level, 3, FLOOR_Y, distance, Blocks.OCHRE_FROGLIGHT);
        }
    }

    private static void buildLandingPad(final ServerLevel level) {
        for (int x = -11; x <= 11; x++) {
            for (int z = -39; z <= -17; z++) {
                final int cornerDistance = Math.abs(x) + Math.abs(z + 28);
                if (cornerDistance > 17) continue;
                final boolean edge = Math.abs(x) == 11 || Math.abs(z + 28) == 11 || cornerDistance == 17;
                place(level, x, FLOOR_Y, z, edge ? Blocks.CONCRETE.orange() : Blocks.CONCRETE.gray());
            }
        }

        fill(level, -4, FLOOR_Y, -32, -3, FLOOR_Y, -24, Blocks.CONCRETE.white());
        fill(level, 3, FLOOR_Y, -32, 4, FLOOR_Y, -24, Blocks.CONCRETE.white());
        fill(level, -3, FLOOR_Y, -29, 3, FLOOR_Y, -28, Blocks.CONCRETE.white());
        for (final int x : new int[] { -10, 10 }) {
            for (final int z : new int[] { -38, -18 })
                place(level, x, FLOOR_Y + 1, z, Blocks.SEA_LANTERN);
        }

    }

    private static void buildHabitat(final ServerLevel level) {
        room(level, -10, -8, 10, 8, ScifiBlocks.HULL_PANEL, ScifiBlocks.HULL_PANEL);
        fill(level, -1, FLOOR_Y + 1, -8, 1, FLOOR_Y + 3, -8, Blocks.AIR);
        fill(level, -1, FLOOR_Y + 1, 8, 1, FLOOR_Y + 3, 8, Blocks.AIR);
        fill(level, -10, FLOOR_Y + 1, -1, -10, FLOOR_Y + 3, 1, Blocks.AIR);
        fill(level, 10, FLOOR_Y + 1, -1, 10, FLOOR_Y + 3, 1, Blocks.AIR);
        fill(level, -7, FLOOR_Y + 2, -8, -3, FLOOR_Y + 3, -8, Blocks.STAINED_GLASS.lightBlue());
        fill(level, 3, FLOOR_Y + 2, -8, 7, FLOOR_Y + 3, -8, Blocks.STAINED_GLASS.lightBlue());
        fill(level, -8, FLOOR_Y + 5, -6, 8, FLOOR_Y + 5, 6, ScifiBlocks.HULL_PANEL);
        fill(level, -2, FLOOR_Y + 5, -5, 2, FLOOR_Y + 5, 5, ScifiBlocks.STRIP_LIGHT);
        fill(level, -8, FLOOR_Y + 1, 4, -3, FLOOR_Y + 1, 6, ScifiBlocks.HULL_PANEL);
        level.setBlock(OXYGEN_STATION, ModBlocks.OXYGEN_CONSOLE.defaultBlockState(), Block.UPDATE_CLIENTS);
        fill(level, -8, FLOOR_Y + 2, 5, -7, FLOOR_Y + 3, 6, Blocks.STAINED_GLASS.cyan());
        fill(level, 4, FLOOR_Y + 1, 4, 7, FLOOR_Y + 1, 6, Blocks.POLISHED_DEEPSLATE);
        place(level, 5, FLOOR_Y + 2, 5, Blocks.SEA_LANTERN);
        place(level, 7, FLOOR_Y + 2, 5, ScifiBlocks.CRYO_CABINET);
        for (final int tankZ : new int[] { 2, 4, 6 })
            place(level, -9, FLOOR_Y + 1, tankZ, ScifiBlocks.AIR_TANK);

        for (final int ventX : new int[] { -6, 6 })
            place(level, ventX, FLOOR_Y + 4, 0, ScifiBlocks.VENT);



        for (final int wallX : new int[] { -10, 10 }) {
            fill(level, wallX, FLOOR_Y + 2, -6, wallX, FLOOR_Y + 3, -3, Blocks.STAINED_GLASS.lightBlue());
            fill(level, wallX, FLOOR_Y + 2, 3, wallX, FLOOR_Y + 3, 6, Blocks.STAINED_GLASS.lightBlue());
        }

        for (int stripZ = -5; stripZ <= 3; stripZ += 2) {
            place(level, -8, FLOOR_Y, stripZ, Blocks.SEA_LANTERN);
            place(level, 8, FLOOR_Y, stripZ, Blocks.SEA_LANTERN);
        }

        room(level, -2, -12, 2, -8, ScifiBlocks.HULL_PANEL, ScifiBlocks.HULL_PANEL);
        installDoorway(level, new DoorSite(new BlockPos(0, FLOOR_Y + 1, -12), Direction.NORTH, Blocks.CONCRETE.white()));
        for (final DoorSite doorway : habitatDoors())
            installDoorway(level, doorway);

    }

    private static void buildPowerYard(final ServerLevel level) {
        fill(level, -46, FLOOR_Y, -10, -38, FLOOR_Y, 10, ScifiBlocks.FLOOR_TILE);
        for (final int panelZ : new int[] { -6, 5 }) {
            for (final int panelX : new int[] { -44, -39 }) {
                place(level, panelX, FLOOR_Y + 1, panelZ, ScifiBlocks.SUPPORT_BEAM);
                fill(level, panelX - 2, FLOOR_Y + 2, panelZ - 2, panelX + 2, FLOOR_Y + 2, panelZ + 2, Blocks.STAINED_GLASS.blue());
                fill(level, panelX, FLOOR_Y + 2, panelZ - 2, panelX, FLOOR_Y + 2, panelZ + 2, ScifiBlocks.SUPPORT_BEAM);
            }
        }

        fill(level, -46, FLOOR_Y, -2, -21, FLOOR_Y, 2, ScifiBlocks.FLOOR_TILE);
        fill(level, -41, FLOOR_Y, 0, -25, FLOOR_Y, 0, ScifiBlocks.CABLE_TRUNK);
        for (final int bankX : new int[] { -29, -27 }) {
            place(level, bankX, FLOOR_Y + 1, 4, ScifiBlocks.BATTERY_BANK);
            place(level, bankX, FLOOR_Y + 1, -4, ScifiBlocks.BATTERY_BANK);
            place(level, bankX, FLOOR_Y + 2, 4, ScifiBlocks.STRIP_LIGHT);
        }

        level.setBlock(RESEARCH_STATION, ModBlocks.RESEARCH_CONSOLE.defaultBlockState(), Block.UPDATE_CLIENTS);
        place(level, -26, FLOOR_Y, 0, ScifiBlocks.FLOOR_LIGHT);
    }

    private static void buildGreenhouse(final ServerLevel level) {
        room(level, 20, -7, 33, 7, ScifiBlocks.HULL_PANEL, ScifiBlocks.DOME_GLASS);
        fill(level, 21, FLOOR_Y + 2, -7, 32, FLOOR_Y + 4, -7, ScifiBlocks.DOME_GLASS);
        fill(level, 21, FLOOR_Y + 2, 7, 32, FLOOR_Y + 4, 7, ScifiBlocks.DOME_GLASS);
        fill(level, 20, FLOOR_Y + 1, -1, 20, FLOOR_Y + 3, 1, Blocks.AIR);
        for (final int bedZ : new int[] { -4, 3 }) {
            fill(level, 22, FLOOR_Y + 1, bedZ, 22, FLOOR_Y + 1, bedZ + 1, ScifiBlocks.HYDROPONIC_TRAY);
            fill(level, 32, FLOOR_Y + 1, bedZ, 32, FLOOR_Y + 1, bedZ + 1, ScifiBlocks.HYDROPONIC_TRAY);
            for (int x = 23; x <= 31; x++) {
                for (int z = bedZ; z <= bedZ + 1; z++) {
                    level.setBlock(new BlockPos(x, FLOOR_Y + 1, z), Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_CLIENTS);
                    level.setBlock(new BlockPos(x, FLOOR_Y + 2, z), Blocks.WHEAT.defaultBlockState()
                            .setValue(BlockStateProperties.AGE_7, 7), Block.UPDATE_CLIENTS);
                }
            }
        }

        fill(level, 24, FLOOR_Y + 1, -1, 32, FLOOR_Y + 1, 1, Blocks.STAINED_GLASS.cyan());
        fill(level, 25, FLOOR_Y + 1, 0, 31, FLOOR_Y + 1, 0, Blocks.WATER);

        installDoorway(level, new DoorSite(new BlockPos(20, FLOOR_Y + 1, 0), Direction.WEST, Blocks.CONCRETE.white()));
    }

    /** Inside the crew shelter's walls, built by {@link #buildShelter}. */
    public static boolean inShelter(final BlockPos position) {
        return position.getX() >= -7 && position.getX() <= 7 && position.getZ() >= 21 && position.getZ() <= 31
                && position.getY() >= FLOOR_Y + 1 && position.getY() <= FLOOR_Y + 4;
    }

    private static void buildShelter(final ServerLevel level) {
        room(level, -8, 20, 8, 32, ScifiBlocks.REINFORCED_HULL, ScifiBlocks.REINFORCED_HULL);
        fill(level, -1, FLOOR_Y + 1, 20, 1, FLOOR_Y + 3, 20, Blocks.AIR);
        fill(level, -7, FLOOR_Y + 4, 20, 7, FLOOR_Y + 4, 20, Blocks.CONCRETE.orange());
        fill(level, -5, FLOOR_Y + 1, 28, 5, FLOOR_Y + 1, 30, ScifiBlocks.CRYO_CABINET);
        for (final int x : new int[] { -5, 0, 5 })
            place(level, x, FLOOR_Y + 2, 29, Blocks.BARREL);

        place(level, 0, FLOOR_Y + 4, 26, Blocks.SEA_LANTERN);

        installDoorway(level, new DoorSite(new BlockPos(0, FLOOR_Y + 1, 20), Direction.NORTH, Blocks.POLISHED_DEEPSLATE));
    }

    private static void buildRoverBay(final ServerLevel level) {
        fill(level, 19, FLOOR_Y, 18, 37, FLOOR_Y, 32, ScifiBlocks.FLOOR_TILE);
        for (final int x : new int[] { 20, 36 }) {
            for (final int z : new int[] { 19, 31 })
                fill(level, x, FLOOR_Y + 1, z, x, FLOOR_Y + 4, z, ScifiBlocks.SUPPORT_BEAM);
        }

        fill(level, 19, FLOOR_Y + 5, 18, 37, FLOOR_Y + 5, 32, ScifiBlocks.HULL_PANEL);
        final var rover = RoverContent.ROVER.create(level, EntitySpawnReason.COMMAND);
        if (rover != null) {
            rover.snapTo(28.5, FLOOR_Y + 1, 25.5, 180, 0);
            if (level.noCollision(rover))
                level.addFreshEntity(rover);
        }

    }

    private static void buildTerrainDetails(final ServerLevel level) {
        for (int x = -44; x <= 44; x++) {
            for (int z = -44; z <= 44; z++) {
                final int distance = Math.abs(x) + Math.abs(z);
                if (distance < 55 || distance > 68 || Math.floorMod(x * 31 + z * 17, 11) > 2) continue;
                place(level, x, FLOOR_Y, z, Blocks.RED_SANDSTONE);
                if (Math.floorMod(x * 7 + z * 13, 5) == 0)
                    place(level, x, FLOOR_Y + 1, z, Blocks.DYED_TERRACOTTA.red());
            }
        }

        fill(level, 39, FLOOR_Y, -17, 41, FLOOR_Y, -11, Blocks.CONCRETE.lightGray());


    }

    private static void buildMissionEquipment(final ServerLevel level) {
        level.setBlock(SAMPLE_DEPOSIT, OutpostContent.SAMPLE_DEPOSIT.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(SAMPLE_ANALYZER, OutpostContent.SAMPLE_ANALYZER.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(SUPPLY_LOCKER, OutpostContent.SUPPLY_LOCKER.defaultBlockState(), Block.UPDATE_CLIENTS);
        fill(level, 12, FLOOR_Y, -6, 16, FLOOR_Y, -2, Blocks.CONCRETE.lightGray());
        level.setBlock(WEATHER_STATION, OutpostContent.WEATHER_TERMINAL.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(-1, 66, -10), ScienceStations.AIRLOCK_CONTROL.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(3, 66, 5), ScienceStations.BACKUP_POWER.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(2, 66, 25), ScienceStations.SHIELD_BENCH.defaultBlockState(), Block.UPDATE_CLIENTS);



    }

    private static void buildWayfinding(final ServerLevel level) {
        for (int x = -6; x >= -24; x -= 6)
            floorArrow(level, x, 0, Direction.WEST, Blocks.CONCRETE.orange());

        for (int z = 12; z <= 18; z += 6)
            floorArrow(level, 0, z, Direction.SOUTH, Blocks.CONCRETE.yellow());

        fill(level, 4, FLOOR_Y, -15, 40, FLOOR_Y, -13, Blocks.CONCRETE.lightGray());
        for (int x = 10; x <= 34; x += 8)
            floorArrow(level, x, -14, Direction.EAST, Blocks.CONCRETE.cyan());

        beacon(level, -18, -4, Blocks.CONCRETE.orange());
        beacon(level, 10, -17, Blocks.CONCRETE.cyan());
        beacon(level, -4, 18, Blocks.CONCRETE.yellow());
        beacon(level, 20, 17, Blocks.CONCRETE.lightBlue());
    }

    private static void floorArrow(final ServerLevel level, final int x, final int z, final Direction direction, final Block color) {
        final BlockPos center = new BlockPos(x, FLOOR_Y, z);
        final Direction side = direction.getClockWise();
        for (int offset = -2; offset <= 1; offset++) {
            final BlockPos point = center.relative(direction, offset);
            place(level, point.getX(), FLOOR_Y, point.getZ(), color);
        }

        for (final int wing : new int[] { -1, 1 }) {
            final BlockPos point = center.relative(side, wing);
            place(level, point.getX(), FLOOR_Y, point.getZ(), color);
        }
    }

    private static void beacon(final ServerLevel level, final int x, final int z, final Block color) {
        place(level, x, FLOOR_Y, z, color);
        level.setBlock(new BlockPos(x, FLOOR_Y + 1, z), OutpostContent.HOLOGRAM_PROJECTOR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static DoorSite[] outpostDoors() {
        return new DoorSite[] {
                habitatDoors()[0], habitatDoors()[1], habitatDoors()[2], habitatDoors()[3],
                new DoorSite(new BlockPos(0, FLOOR_Y + 1, -12), Direction.NORTH, Blocks.CONCRETE.white()),
                new DoorSite(new BlockPos(0, FLOOR_Y + 1, 20), Direction.NORTH, Blocks.POLISHED_DEEPSLATE),
                new DoorSite(new BlockPos(20, FLOOR_Y + 1, 0), Direction.WEST, Blocks.CONCRETE.white())
        };
    }

    private static void migrateLaserDoors(final ServerLevel level) {
        for (final DoorSite door : outpostDoors()) {
            if (doorInstalled(level, door))
                installDoorway(level, door);
        }
    }

    private static DoorSite[] habitatDoors() {
        return new DoorSite[] {
                new DoorSite(new BlockPos(0, FLOOR_Y + 1, -8), Direction.NORTH, Blocks.CONCRETE.white()),
                new DoorSite(new BlockPos(0, FLOOR_Y + 1, 8), Direction.SOUTH, Blocks.CONCRETE.white()),
                new DoorSite(new BlockPos(-10, FLOOR_Y + 1, 0), Direction.WEST, Blocks.CONCRETE.white()),
                new DoorSite(new BlockPos(10, FLOOR_Y + 1, 0), Direction.EAST, Blocks.CONCRETE.white())
        };
    }

    private static void installDoorway(final ServerLevel level, final DoorSite site) {
        final Direction side = site.facing.getClockWise();
        if (!doorInstalled(level, site)) {
            for (int width = -1; width <= 1; width++) {
                for (int height = 0; height <= 2; height++) {
                    final BlockPos position = site.center.relative(side, width).above(height);
                    level.setBlock(position, ScifiBlocks.SUPPORT_BEAM.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }

        for (int index = 0; index < 2; index++) {
            final BlockPos foot = site.center.relative(side, index - 1);
            final var door = ScifiBlocks.AIRLOCK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, site.facing)
                    .setValue(DoorBlock.HINGE, index == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
                    .setValue(DoorBlock.OPEN, false).setValue(BlockStateProperties.POWERED, false);
            level.setBlock(foot, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(foot.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.scheduleTick(foot, ScifiBlocks.AIRLOCK_DOOR, 5);
            for (final Direction approach : new Direction[] { site.facing, site.facing.getOpposite() }) {
                final BlockPos plate = foot.relative(approach);
                if (level.getBlockState(plate).is(Blocks.STONE_PRESSURE_PLATE))
                    level.setBlock(plate, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private record DoorSite(BlockPos center, Direction facing, Block wall) {
        /* w */
    }

    private record EquipmentSite(BlockPos position, Block replacement, Block prototype) {
        /* w */
    }

    private record LegacySign(BlockPos position, List<String> lines) {
        /* w */
    }

    private static void room(final ServerLevel level, final int minX, final int minZ, final int maxX, final int maxZ,
                             final Block walls, final Block roof) {
        fill(level, minX, FLOOR_Y, minZ, maxX, FLOOR_Y, maxZ, ScifiBlocks.FLOOR_TILE);
        fill(level, minX, FLOOR_Y + 1, minZ, maxX, FLOOR_Y + 4, minZ, walls);
        fill(level, minX, FLOOR_Y + 1, maxZ, maxX, FLOOR_Y + 4, maxZ, walls);
        fill(level, minX, FLOOR_Y + 1, minZ, minX, FLOOR_Y + 4, maxZ, walls);
        fill(level, maxX, FLOOR_Y + 1, minZ, maxX, FLOOR_Y + 4, maxZ, walls);
        fill(level, minX, FLOOR_Y + 5, minZ, maxX, FLOOR_Y + 5, maxZ, roof);
        fill(level, minX, FLOOR_Y, minZ, maxX, FLOOR_Y, minZ, Blocks.POLISHED_DEEPSLATE);
        fill(level, minX, FLOOR_Y, maxZ, maxX, FLOOR_Y, maxZ, Blocks.POLISHED_DEEPSLATE);
        fill(level, minX, FLOOR_Y, minZ, minX, FLOOR_Y, maxZ, Blocks.POLISHED_DEEPSLATE);
        fill(level, maxX, FLOOR_Y, minZ, maxX, FLOOR_Y, maxZ, Blocks.POLISHED_DEEPSLATE);
        for (final int cornerX : new int[] { minX, maxX }) {
            for (final int cornerZ : new int[] { minZ, maxZ })
                fill(level, cornerX, FLOOR_Y + 1, cornerZ, cornerX, FLOOR_Y + 4, cornerZ, ScifiBlocks.SUPPORT_BEAM);
        }

        for (int supportX = minX + 2; supportX < maxX; supportX += 4)
            fill(level, supportX, FLOOR_Y + 5, minZ, supportX, FLOOR_Y + 5, maxZ, ScifiBlocks.SUPPORT_BEAM);
    }

    private static void fill(final ServerLevel level, final int minX, final int minY, final int minZ,
                             final int maxX, final int maxY, final int maxZ, final Block block) {
        for (final BlockPos position : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ))
            place(level, position.getX(), position.getY(), position.getZ(), block);
    }

    private static void place(final ServerLevel level, final int x, final int y, final int z, final Block block) {
        if (Math.abs(x) > SITE_RADIUS || Math.abs(z) > SITE_RADIUS || y < FLOOR_Y || y > FLOOR_Y + OutpostDome.HEIGHT)
            throw new IllegalArgumentException("Outpost placement outside its reserved build bounds");

        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void verifyArrival(final ServerLevel level) {
        final BlockPos floor = new BlockPos(0, FLOOR_Y, -28);
        if (!level.getBlockState(floor).is(Blocks.CONCRETE.white())
                || !level.getBlockState(floor.above()).isAir() || !level.getBlockState(floor.above(2)).isAir()
                || !(level.getBlockState(RESEARCH_STATION).is(ModBlocks.RESEARCH_CONSOLE)
                || level.getBlockState(RESEARCH_STATION).is(Blocks.DAYLIGHT_DETECTOR))
                || !(level.getBlockState(OXYGEN_STATION).is(ModBlocks.OXYGEN_CONSOLE)
                || level.getBlockState(OXYGEN_STATION).is(Blocks.LODESTONE)))
            throw new IllegalStateException("Outpost arrival or equipment check failed. Existing terrain was preserved; ask an operator to inspect the training site.");
    }

    private static int readConstructionStage(final MinecraftServer server) throws IOException {
        final Path state = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova-mars-outpost.state");
        if (!Files.exists(state)) return 0;
        return switch (Files.readString(state).strip()) {
            case "building" -> 1;
            case "ready" -> 2;
            default -> throw new IOException("Unknown outpost construction state; existing blocks preserved.");
        };
    }

    private static void writeConstructionStage(final MinecraftServer server, final boolean completed) throws IOException {
        final Path state = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova-mars-outpost.state");
        if (!completed) {
            Files.writeString(state, "building\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return;
        }

        final Path temporary = state.resolveSibling("nimbus-nova-mars-outpost.state.tmp");
        Files.writeString(temporary, "ready\n");
        Files.move(temporary, state, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    // shortcut: exact return positions last for this process; a safe world-spawn search handles later visits.
    private record ReturnPoint(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
        /* w */
    }
}
