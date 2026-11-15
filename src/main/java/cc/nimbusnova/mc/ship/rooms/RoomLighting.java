package cc.nimbusnova.mc.ship.rooms;

import cc.nimbusnova.mc.NimbusNovaMod;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState.Room;
import cc.nimbusnova.mc.world.ship.ShipMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
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
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@NullMarked
public final class RoomLighting {
    public static final Block LAMP = register("lighting_lamp", false);
    public static final Block SWITCH = register("lighting_switch", true);
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();

    private RoomLighting() {
        /* w */
    }

    private static Block register(final String name, final boolean control) {
        final Identifier id = Identifier.parse("nimbus_nova:" + name);
        final ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        final BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().setId(key).strength(3).sound(SoundType.METAL);
        final Block block = control ? new SwitchBlock(properties.noCollision().lightLevel(_ -> 1))
                : new LampBlock(properties.lightLevel(state -> !state.getValue(BlockStateProperties.LIT) ? 0
                : state.getValue(BlockStateProperties.POWERED) ? 3 : 12));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(LAMP);
            output.accept(SWITCH);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(RoomLighting::recover);
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !level.dimension().equals(ShipMap.DIMENSION)
                    || !level.getBlockState(hit.getBlockPos()).is(SWITCH)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer crew)) return InteractionResult.SUCCESS;
            final Session session = SESSIONS.get(crew.level().getServer());
            if (session == null || player.distanceToSqr(Vec3.atCenterOf(hit.getBlockPos())) > 36) return InteractionResult.FAIL;
            final Room room = session.switches.getOrDefault(hit.getBlockPos(), ShipRooms.roomAt(hit.getBlockPos()));
            if (room == null) return InteractionResult.FAIL;
            if (!crew.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                    && ShipController.get(crew.level().getServer()).role(crew) == null) return InteractionResult.FAIL;
            if (crew.isShiftKeyDown()) {
                session.steady = !session.steady;
                crew.sendOverlayMessage(Component.literal(session.steady ? "Steady emergency lighting enabled" : "Brief low-frequency fault pulses enabled"));
            } else if (room == Room.CONTROL) {
                final boolean enabled = !session.enabled.getOrDefault(room, true);
                for (final Room target : Room.values()) session.enabled.put(target, enabled);
                crew.sendOverlayMessage(Component.literal(enabled ? "Cabin lights enabled" : "Cabin lights off / emergency markers remain powered"));
            } else {
                final boolean enabled = !session.enabled.getOrDefault(room, true);
                session.enabled.put(room, enabled);
                crew.sendOverlayMessage(Component.literal(room.name().replace('_', ' ') + (enabled ? " lights on" : " lights off")));
            }
            crew.level().playSound(null, hit.getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.35F, 1.1F);
            apply(crew.level().getServer(), session, false);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    public static void tick(final MinecraftServer server, final boolean paused) {
        final ServerLevel level = server.getLevel(ShipMap.DIMENSION);
        if (level == null) return;
        final Session session = SESSIONS.computeIfAbsent(server, _ -> install(server, level));
        if (!session.ready) return;
        if (!paused) session.seconds++;
        for (final Room room : Room.values()) {
            final boolean powered = ShipRooms.state(server).powered(room);
            final int pressure = ShipRooms.state(server).pressure(room);
            final Boolean previous = session.previousPower.put(room, powered);
            final boolean danger = pressure < 80;
            final Boolean previousDanger = session.previousDanger.put(room, danger);
            final BlockPos soundAt = session.anchors.get(room);
            if (previous != null && previous != powered) {
                session.faultStarted.put(room, session.seconds);
                if (soundAt != null)
                    level.playSound(null, soundAt, powered ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.BLOCKS, 0.35F, powered ? 1.1F : 0.8F);
            }
            if (soundAt != null && !paused && danger && (previousDanger == null || !previousDanger || session.seconds % 12 == 0))
                level.playSound(null, soundAt, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.24F, 0.6F);
        }
        final BlockPos control = session.anchors.get(Room.CONTROL);
        if (!paused && control != null && ShipRooms.hasUnresolved(server) && session.seconds % 12 == 0)
            level.playSound(null, control, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.2F, 0.7F);
        apply(server, session, paused);
    }

    public static int fixtureCount(final MinecraftServer server) {
        final Session session = SESSIONS.get(server);
        return session == null ? 0 : session.fixtures.size();
    }

    public static BlockPos switchPosition(final MinecraftServer server, final Room room) {
        final Session session = SESSIONS.get(server);
        if (session == null) throw new IllegalStateException("Room lighting is not installed yet");
        return session.switches.entrySet().stream().filter(entry -> entry.getValue() == room).findFirst().orElseThrow().getKey();
    }

    private static Session install(final MinecraftServer server, final ServerLevel level) {
        final Session session = new Session();
        final Path marker = marker(server);
        try {
            if (Files.exists(marker)) recover(server);
            if (Files.exists(marker)) throw new IOException("Previous lighting ownership could not be recovered");
            final List<BlockPos> doors = new ArrayList<>();
            for (final BlockPos mutable : BlockPos.betweenClosed(-13, 64, -41, 12, 94, 40)) {
                final BlockState existing = level.getBlockState(mutable);
                if (existing.getBlock() instanceof DoorBlock && existing.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                    doors.add(mutable.immutable());
                final Room room = ShipRooms.roomAt(mutable);
                if (room == null) continue;
                final BlockState state = level.getBlockState(mutable);
                if (!state.is(LAMP) && !state.is(Blocks.LIGHT) && !state.is(Blocks.SEA_LANTERN) && !state.is(Blocks.GLOWSTONE)
                        && !state.is(Blocks.OCHRE_FROGLIGHT) && !state.is(Blocks.VERDANT_FROGLIGHT) && !state.is(Blocks.PEARLESCENT_FROGLIGHT)) continue;
                final BlockPos position = mutable.immutable();
                session.fixtures.add(new Fixture(position, state, room));
                session.anchors.putIfAbsent(room, position);
            }
            for (final Room room : Room.values()) {
                final BlockPos anchor = session.anchors.get(room);
                if (anchor == null) continue;
                BlockPos preferred = anchor;
                double doorDistance = Double.MAX_VALUE;
                for (final BlockPos door : doors) {
                    boolean adjacent = ShipRooms.roomAt(door) == room;
                    for (final Direction direction : Direction.Plane.HORIZONTAL)
                        for (int distance = 1; distance <= 3; distance++)
                            adjacent |= ShipRooms.roomAt(door.relative(direction, distance)) == room;
                    if (adjacent && door.distSqr(anchor) < doorDistance) {
                        preferred = door;
                        doorDistance = door.distSqr(anchor);
                    }
                }
                BlockPos chosen = null;
                Direction facing = Direction.NORTH;
                for (final BlockPos mutable : BlockPos.betweenClosed(-12, 77, -40, 11, 85, 34)) {
                    if (ShipRooms.roomAt(mutable) != room || !level.getBlockState(mutable).isAir()) continue;
                    if (!level.getBlockState(mutable.below()).getCollisionShape(level, mutable.below()).isEmpty()) continue;
                    if (!level.getBlockState(mutable.below(2)).isFaceSturdy(level, mutable.below(2), Direction.UP)) continue;
                    for (final Direction direction : Direction.Plane.HORIZONTAL) {
                        final BlockPos support = mutable.relative(direction.getOpposite());
                        if (!level.getBlockState(support).isFaceSturdy(level, support, direction)) continue;
                        if (chosen == null || mutable.distSqr(preferred) < chosen.distSqr(preferred)) {
                            chosen = mutable.immutable();
                            facing = direction;
                        }
                    }
                }
                if (chosen != null) {
                    session.switches.put(chosen, room);
                    session.switchStates.put(chosen, SWITCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
                }
            }
            final JsonArray entries = new JsonArray();
            for (final Fixture fixture : session.fixtures) {
                final JsonObject item = new JsonObject();
                item.addProperty("position", fixture.position.asLong());
                item.addProperty("state", BlockStateParser.serialize(fixture.original));
                entries.add(item);
            }
            for (final BlockPos position : session.switches.keySet()) {
                final JsonObject item = new JsonObject();
                item.addProperty("position", position.asLong());
                item.addProperty("state", "minecraft:air");
                entries.add(item);
            }
            Files.createDirectories(marker.getParent());
            final Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
            Files.writeString(temporary, entries.toString());
            Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            for (final Fixture fixture : session.fixtures) {
                if (!fixture.original.is(Blocks.LIGHT))
                    level.setBlock(fixture.position, LAMP.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (final var entry : session.switchStates.entrySet())
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
            session.ready = true;
        } catch (final IOException error) {
            NimbusNovaMod.LOGGER.warn("Room lighting installation preserved existing blocks: {}", error.getMessage());
        }
        return session;
    }

    private static void apply(final MinecraftServer server, final Session session, final boolean paused) {
        final ServerLevel level = server.getLevel(ShipMap.DIMENSION);
        if (level == null) return;
        for (int index = 0; index < session.fixtures.size(); index++) {
            final Fixture fixture = session.fixtures.get(index);
            final boolean powered = ShipRooms.state(server).powered(fixture.room);
            final boolean enabled = session.enabled.getOrDefault(fixture.room, true);
            final int age = session.seconds - session.faultStarted.getOrDefault(fixture.room, -100);
            // Two dim startup pulses, never a sustained strobe or a full-bright flash.
            final boolean pulse = !session.steady && !paused && !powered && age >= 0 && age < 3 && age % 2 == 0;
            final boolean backup = !powered || !enabled;
            final int brightness = backup ? index % 12 == 0 ? pulse ? 1 : 3 : 0 : 12;
            final BlockState current = level.getBlockState(fixture.position);
            final BlockState desired;
            if (fixture.original.is(Blocks.LIGHT)) {
                if (!current.is(Blocks.LIGHT)) continue;
                desired = current.setValue(BlockStateProperties.LEVEL, brightness);
            } else {
                if (!current.is(LAMP)) continue;
                desired = current.setValue(BlockStateProperties.LIT, brightness > 0).setValue(BlockStateProperties.POWERED, backup);
            }
            if (!current.equals(desired)) level.setBlock(fixture.position, desired, Block.UPDATE_ALL);
        }
    }

    public static void clear(final MinecraftServer server) {
        SESSIONS.remove(server);
        recover(server);
    }

    public static void recover(final MinecraftServer server) {
        final Path marker = marker(server);
        if (!Files.exists(marker)) return;
        final ServerLevel level = server.getLevel(ShipMap.DIMENSION);
        if (level == null) return;
        try {
            if (Files.size(marker) > 2_000_000) throw new IOException("Lighting marker exceeds its bound");
            final JsonArray entries = JsonParser.parseString(Files.readString(marker)).getAsJsonArray();
            if (entries.size() > 4096) throw new IOException("Too many owned lighting cells");
            for (final var entry : entries) {
                final JsonObject item = entry.getAsJsonObject();
                final BlockPos position = BlockPos.of(item.get("position").getAsLong());
                if (position.getX() < -13 || position.getX() > 12 || position.getY() < 64 || position.getY() > 94
                        || position.getZ() < -41 || position.getZ() > 40) throw new IOException("Lighting marker outside ship bounds");
                final BlockState original = BlockStateParser.parseForBlock(server.registryAccess().lookupOrThrow(Registries.BLOCK), item.get("state").getAsString(), false).blockState();
                final BlockState current = level.getBlockState(position);
                final boolean ownedLight = original.is(Blocks.LIGHT) && current.is(Blocks.LIGHT)
                        && (current.equals(original) || switch (current.getValue(BlockStateProperties.LEVEL)) {
                            case 0, 1, 3, 12 -> true;
                            default -> false;
                        });
                if (current.is(LAMP) || current.is(SWITCH) || ownedLight)
                    level.setBlock(position, original, Block.UPDATE_ALL);
            }
            Files.delete(marker);
            SESSIONS.remove(server);
        } catch (final IOException | CommandSyntaxException | RuntimeException error) {
            NimbusNovaMod.LOGGER.warn("Lighting recovery preserved unknown player blocks: {}", error.getMessage());
        }
    }

    private static Path marker(final MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/ship-lighting.json");
    }

    private record Fixture(BlockPos position, BlockState original, Room room) {
        /* w */
    }

    private static final class Session {
        private final List<Fixture> fixtures = new ArrayList<>();
        private final Map<BlockPos, Room> switches = new HashMap<>();
        private final Map<BlockPos, BlockState> switchStates = new HashMap<>();
        private final Map<Room, Boolean> enabled = new EnumMap<>(Room.class), previousPower = new EnumMap<>(Room.class), previousDanger = new EnumMap<>(Room.class);
        private final Map<Room, BlockPos> anchors = new EnumMap<>(Room.class);
        private final Map<Room, Integer> faultStarted = new EnumMap<>(Room.class);
        private int seconds;
        private boolean ready, steady;

        private Session() {
            for (final Room room : Room.values()) previousPower.put(room, true);
        }
    }

    private static final class LampBlock extends Block {
        private LampBlock(final Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT, true).setValue(BlockStateProperties.POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.LIT, BlockStateProperties.POWERED);
        }
    }

    private static final class SwitchBlock extends HorizontalDirectionalBlock {
        private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(3, 3, 13, 13, 13, 16));

        private SwitchBlock(final Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos position, final CollisionContext context) {
            return SHAPES.get(state.getValue(FACING));
        }

        @Override
        public BlockState getStateForPlacement(final BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
    }
}
