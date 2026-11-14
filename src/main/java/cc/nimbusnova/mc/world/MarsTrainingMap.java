package cc.nimbusnova.mc.world;

import cc.nimbusnova.mc.content.ModBlocks;
import cc.nimbusnova.mc.mission.MissionController;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MarsTrainingMap {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.parse("nimbus_nova:mars_training"));
    public static final BlockPos RESEARCH_STATION = new BlockPos(-24, 66, 0);
    public static final BlockPos OXYGEN_STATION = new BlockPos(-5, 66, 3);
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
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> dispatcher.register(Commands.literal("mars")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Mars training: /mars create (operator), /mars join, /mars leave. "
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

    private static int create(final CommandSourceStack source) throws IOException {
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
                SITE_RADIUS, FLOOR_Y + 10, SITE_RADIUS)) {
            if (level.getBlockState(position).isAir()) continue;
            source.sendFailure(Component.literal("The reserved Mars site contains existing blocks. Creation stopped without replacing them."));
            return 0;
        }

        // Claim the site on disk before placing blocks, so an interrupted build never overwrites later player work.
        writeConstructionStage(source.getServer(), false);
        source.sendSuccess(() -> Component.literal("Building the original Mars training outpost..."), false);

        buildPaths(level);
        buildLandingPad(level);
        buildHabitat(level);
        buildPowerYard(level);
        buildGreenhouse(level);
        buildShelter(level);
        buildRoverBay(level);
        buildTerrainDetails(level);
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
        return 1;
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
        fill(level, -2, FLOOR_Y, -28, 2, FLOOR_Y, 30, Blocks.CONCRETE.lightGray());
        fill(level, -32, FLOOR_Y, -2, 32, FLOOR_Y, 2, Blocks.CONCRETE.lightGray());
        fill(level, 0, FLOOR_Y, 23, 30, FLOOR_Y, 27, Blocks.CONCRETE.lightGray());
        for (int distance = -24; distance <= 24; distance += 8) {
            place(level, -3, FLOOR_Y, distance, Blocks.OCHRE_FROGLIGHT);
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

        sign(level, -8, FLOOR_Y + 1, -18, "ASTER OUTPOST", "Landing pad", "Training terrain", "/mars leave");
    }

    private static void buildHabitat(final ServerLevel level) {
        room(level, -10, -8, 10, 8, Blocks.CONCRETE.white(), Blocks.CONCRETE.gray());
        fill(level, -1, FLOOR_Y + 1, -8, 1, FLOOR_Y + 3, -8, Blocks.AIR);
        fill(level, -1, FLOOR_Y + 1, 8, 1, FLOOR_Y + 3, 8, Blocks.AIR);
        fill(level, -10, FLOOR_Y + 1, -1, -10, FLOOR_Y + 3, 1, Blocks.AIR);
        fill(level, 10, FLOOR_Y + 1, -1, 10, FLOOR_Y + 3, 1, Blocks.AIR);
        fill(level, -7, FLOOR_Y + 2, -8, -3, FLOOR_Y + 3, -8, Blocks.STAINED_GLASS.lightBlue());
        fill(level, 3, FLOOR_Y + 2, -8, 7, FLOOR_Y + 3, -8, Blocks.STAINED_GLASS.lightBlue());
        fill(level, -8, FLOOR_Y + 5, -6, 8, FLOOR_Y + 5, 6, Blocks.CONCRETE.orange());
        fill(level, -2, FLOOR_Y + 5, -5, 2, FLOOR_Y + 5, 5, Blocks.SEA_LANTERN);
        fill(level, -8, FLOOR_Y + 1, 4, -3, FLOOR_Y + 1, 6, Blocks.SMOOTH_QUARTZ);
        level.setBlock(OXYGEN_STATION, ModBlocks.OXYGEN_CONSOLE.defaultBlockState(), Block.UPDATE_CLIENTS);
        fill(level, -8, FLOOR_Y + 2, 5, -7, FLOOR_Y + 3, 6, Blocks.STAINED_GLASS.cyan());
        fill(level, 4, FLOOR_Y + 1, 4, 7, FLOOR_Y + 1, 6, Blocks.POLISHED_DEEPSLATE);
        place(level, 5, FLOOR_Y + 2, 5, Blocks.SEA_LANTERN);
        place(level, 7, FLOOR_Y + 2, 5, Blocks.CRAFTER);
        sign(level, -4, FLOOR_Y + 2, 6, "OXYGEN SYSTEM", "Right-click stone", "Life-support role", "Shared reserves");
        sign(level, 4, FLOOR_Y + 2, 6, "MISSION CONTROL", "Press J", "Select a role", "Prepare the crew");
        sign(level, -4, FLOOR_Y + 1, -6, "ASTER HABITAT", "Fictional site", "Authored terrain", "No suit hazard yet");
    }

    private static void buildPowerYard(final ServerLevel level) {
        fill(level, -37, FLOOR_Y, -10, -17, FLOOR_Y, 10, Blocks.CONCRETE.gray());
        for (int panelX = -34; panelX <= -22; panelX += 6) {
            for (final int panelZ : new int[] { -6, 5 }) {
                place(level, panelX, FLOOR_Y + 1, panelZ, Blocks.POLISHED_BLACKSTONE);
                fill(level, panelX - 2, FLOOR_Y + 2, panelZ - 2, panelX + 2, FLOOR_Y + 2, panelZ + 2, Blocks.STAINED_GLASS.blue());
                fill(level, panelX, FLOOR_Y + 2, panelZ - 2, panelX, FLOOR_Y + 2, panelZ + 2, Blocks.POLISHED_BLACKSTONE);
            }
        }

        fill(level, -27, FLOOR_Y, -1, -21, FLOOR_Y, 1, Blocks.POLISHED_DEEPSLATE);
        level.setBlock(RESEARCH_STATION, ModBlocks.RESEARCH_CONSOLE.defaultBlockState(), Block.UPDATE_CLIENTS);
        place(level, -26, FLOOR_Y + 1, 0, Blocks.SEA_LANTERN);
        sign(level, -20, FLOOR_Y + 1, 0, "POWER / RESEARCH", "Right-click console", "Power engineer", "Research uses power");
        sign(level, -35, FLOOR_Y + 1, 0, "SOLAR ARRAY", "Dust lowers supply", "Panels are scenery", "J: live simulation");
    }

    private static void buildGreenhouse(final ServerLevel level) {
        room(level, 20, -8, 36, 8, Blocks.CONCRETE.white(), Blocks.STAINED_GLASS.green());
        fill(level, 20, FLOOR_Y + 2, -6, 36, FLOOR_Y + 4, -6, Blocks.GLASS);
        fill(level, 20, FLOOR_Y + 2, 6, 36, FLOOR_Y + 4, 6, Blocks.GLASS);
        fill(level, 20, FLOOR_Y + 1, -1, 20, FLOOR_Y + 3, 1, Blocks.AIR);
        for (final int bedZ : new int[] { -4, 3 }) {
            fill(level, 23, FLOOR_Y + 1, bedZ, 33, FLOOR_Y + 1, bedZ + 1, Blocks.FARMLAND);
            for (int x = 23; x <= 33; x++) {
                for (int z = bedZ; z <= bedZ + 1; z++) {
                    level.setBlock(new BlockPos(x, FLOOR_Y + 1, z), Blocks.FARMLAND.defaultBlockState()
                            .setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_CLIENTS);
                    level.setBlock(new BlockPos(x, FLOOR_Y + 2, z), Blocks.WHEAT.defaultBlockState()
                            .setValue(BlockStateProperties.AGE_7, 7), Block.UPDATE_CLIENTS);
                }
            }
        }

        fill(level, 24, FLOOR_Y + 1, -1, 32, FLOOR_Y + 1, 1, Blocks.STAINED_GLASS.cyan());
        fill(level, 25, FLOOR_Y + 1, 0, 31, FLOOR_Y + 1, 0, Blocks.WATER);
        sign(level, 21, FLOOR_Y + 1, -3, "GREENHOUSE", "Demonstration crops", "Food model planned", "Water is limited");
    }

    /** Inside the crew shelter's walls, built by {@link #buildShelter}. */
    public static boolean inShelter(final BlockPos position) {
        return position.getX() >= -7 && position.getX() <= 7 && position.getZ() >= 21 && position.getZ() <= 31
                && position.getY() >= FLOOR_Y + 1 && position.getY() <= FLOOR_Y + 4;
    }

    private static void buildShelter(final ServerLevel level) {
        room(level, -8, 20, 8, 32, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES);
        fill(level, -1, FLOOR_Y + 1, 20, 1, FLOOR_Y + 3, 20, Blocks.AIR);
        fill(level, -7, FLOOR_Y + 4, 20, 7, FLOOR_Y + 4, 20, Blocks.CONCRETE.orange());
        fill(level, -5, FLOOR_Y + 1, 28, 5, FLOOR_Y + 1, 30, Blocks.SMOOTH_QUARTZ);
        for (final int x : new int[] { -5, 0, 5 })
            place(level, x, FLOOR_Y + 2, 29, Blocks.BARREL);

        place(level, 0, FLOOR_Y + 4, 26, Blocks.SEA_LANTERN);
        sign(level, -4, FLOOR_Y + 1, 18, "CREW SHELTER", "Solar storm? Get in", "No consoles inside", "Plan before alerts");
    }

    private static void buildRoverBay(final ServerLevel level) {
        fill(level, 19, FLOOR_Y, 18, 37, FLOOR_Y, 32, Blocks.CONCRETE.gray());
        for (final int x : new int[] { 20, 36 }) {
            for (final int z : new int[] { 19, 31 })
                fill(level, x, FLOOR_Y + 1, z, x, FLOOR_Y + 4, z, Blocks.IRON_BLOCK);
        }

        fill(level, 19, FLOOR_Y + 5, 18, 37, FLOOR_Y + 5, 32, Blocks.SMOOTH_STONE);
        final var rover = RoverContent.ROVER.create(level, EntitySpawnReason.COMMAND);
        if (rover != null) {
            rover.snapTo(28.5, FLOOR_Y + 1, 25.5, 180, 0);
            if (level.noCollision(rover))
                level.addFreshEntity(rover);
        }

        sign(level, 20, FLOOR_Y + 1, 17, "ROVER WORKSHOP", "Right-click to board", "Movement keys drive", "Sneak to exit");
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
        place(level, 40, FLOOR_Y + 1, -16, Blocks.COPPER_ORE);
        sign(level, 39, FLOOR_Y + 1, -12, "SCIENCE SITE", "Inspect rock layers", "Fictional samples", "Return to habitat");
        sign(level, 5, FLOOR_Y + 1, -12, "OUTPOST ROUTES", "Habitat: ahead", "Power: left", "Greenhouse: right");
    }

    private static void room(final ServerLevel level, final int minX, final int minZ, final int maxX, final int maxZ,
                             final Block walls, final Block roof) {
        fill(level, minX, FLOOR_Y, minZ, maxX, FLOOR_Y, maxZ, Blocks.CONCRETE.lightGray());
        fill(level, minX, FLOOR_Y + 1, minZ, maxX, FLOOR_Y + 4, minZ, walls);
        fill(level, minX, FLOOR_Y + 1, maxZ, maxX, FLOOR_Y + 4, maxZ, walls);
        fill(level, minX, FLOOR_Y + 1, minZ, minX, FLOOR_Y + 4, maxZ, walls);
        fill(level, maxX, FLOOR_Y + 1, minZ, maxX, FLOOR_Y + 4, maxZ, walls);
        fill(level, minX, FLOOR_Y + 5, minZ, maxX, FLOOR_Y + 5, maxZ, roof);
    }

    private static void sign(final ServerLevel level, final int x, final int y, final int z,
                             final String first, final String second, final String third, final String fourth) {
        final BlockPos position = new BlockPos(x, y, z);
        level.setBlock(position, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (!(level.getBlockEntity(position) instanceof SignBlockEntity sign))
            throw new IllegalStateException("Could not create the outpost sign at " + position);

        final SignText text = SignText.EMPTY.asMutable().setColor(DyeColor.WHITE).setTextGlowing(true)
                .setLine(0, Component.literal(first)).setLine(1, Component.literal(second))
                .setLine(2, Component.literal(third)).setLine(3, Component.literal(fourth)).asImmutable();
        sign.setText(text, SignTextSlot.FRONT);
        sign.setText(text, SignTextSlot.BACK);
        sign.setChanged();
    }

    private static void fill(final ServerLevel level, final int minX, final int minY, final int minZ,
                             final int maxX, final int maxY, final int maxZ, final Block block) {
        for (final BlockPos position : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ))
            place(level, position.getX(), position.getY(), position.getZ(), block);
    }

    private static void place(final ServerLevel level, final int x, final int y, final int z, final Block block) {
        if (Math.abs(x) > SITE_RADIUS || Math.abs(z) > SITE_RADIUS || y < FLOOR_Y || y > FLOOR_Y + 10)
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
