package cc.nimbusnova.mc.ship.rooms;

import cc.nimbusnova.mc.NimbusNovaMod;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.content.ScifiBlocks;
import cc.nimbusnova.mc.content.ShipContent;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.ship.ShipMission;
import cc.nimbusnova.mc.ship.ShipTask;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState.DoorMode;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState.Fault;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState.Room;
import cc.nimbusnova.mc.world.ship.ShipMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Prediction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Authored compartments in the supplied ship, not a general airtight-volume simulation. */
public final class ShipRooms {
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();
    private static final Map<Room, AABB> BOUNDS = new EnumMap<>(Room.class);
    private static final BlockPos BREACH = new BlockPos(11, 77, -12);
    private static final BlockPos WIRE = new BlockPos(-8, 82, -38);

    static {
        BOUNDS.put(Room.BRIDGE, new AABB(-12, 79, 19, 13, 87, 35));
        BOUNDS.put(Room.ENGINEERING, new AABB(-12, 80, -41, 13, 88, -21));
        BOUNDS.put(Room.MEDICAL, new AABB(3, 81, 2, 12, 86, 16));
        BOUNDS.put(Room.GALLEY, new AABB(-12, 81, 2, -2, 87, 17));
        BOUNDS.put(Room.CONTROL, new AABB(3, 81, -15, 12, 86, 2));
        BOUNDS.put(Room.COMMS, new AABB(-12, 76, -22, -2, 80, -2));
        BOUNDS.put(Room.SCIENCE, new AABB(3, 76, -22, 12, 80, -2));
        BOUNDS.put(Room.HYDROPONICS, new AABB(-12, 76, -2, -2, 80, 8));
        BOUNDS.put(Room.STORAGE, new AABB(3, 76, 8, 12, 80, 18));
        BOUNDS.put(Room.RECREATION, new AABB(-12, 76, 8, -2, 80, 18));
    }

    private ShipRooms() {
        /* w */
    }

    public static ShipRoomState state(final MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, _ -> new Session()).state;
    }

    public static BlockPos breachPosition() {
        return BREACH;
    }

    public static BlockPos wirePosition() {
        return WIRE;
    }

    public static @Nullable Room roomAt(final BlockPos position) {
        for (final var entry : BOUNDS.entrySet())
            if (entry.getValue().contains(Vec3.atCenterOf(position))) return entry.getKey();
        return null;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(RoomStatusPayload.TYPE, RoomStatusPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RoomActionPayload.TYPE, RoomActionPayload.CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerLifecycleEvents.SERVER_STARTED.register(ShipRooms::recoverOwnedBreach);
        ServerPlayNetworking.registerGlobalReceiver(RoomActionPayload.TYPE, (packet, context) -> context.server().execute(() -> {
            final ServerPlayer player = context.player();
            if (!player.level().dimension().equals(ShipMap.DIMENSION)) return;
            try {
                if (packet.action().equals("status")) {
                    send(player, true);
                    return;
                }
                final Room room = Room.valueOf(packet.room());
                if (packet.action().equals("power") || packet.action().equals("breach")) {
                    if (!operator(player)) throw new IllegalStateException("Mission Control schedules emergencies.");
                    trigger(context.server(), room.name(), packet.action());
                } else {
                    if (!operator(player) && ShipController.get(context.server()).role(player) == null)
                        throw new IllegalStateException("Choose a crew role before operating room doors.");
                    final DoorMode mode = switch (packet.action()) {
                        case "auto" -> DoorMode.AUTO;
                        case "open" -> DoorMode.OPEN;
                        case "close" -> DoorMode.CLOSED;
                        default -> throw new IllegalArgumentException("Unknown room control.");
                    };
                    if (mode == DoorMode.OPEN && state(context.server()).pressure(room) < 80)
                        throw new IllegalStateException("Pressure interlock active. Patch the leak and allow pressure to recover, or crank a door for evacuation.");
                    state(context.server()).doorMode(room, mode);
                }
                for (final ServerPlayer crew : context.server().getPlayerList().getPlayers()) send(crew, false);
            } catch (final IllegalArgumentException | IllegalStateException error) {
                player.sendSystemMessage(Component.literal(error.getMessage()));
                send(player, false);
            }
        }));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !level.dimension().equals(ShipMap.DIMENSION)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer crew)) return InteractionResult.PASS;
            final BlockPos position = hit.getBlockPos();
            if (player.distanceToSqr(Vec3.atCenterOf(position)) > 36) return InteractionResult.PASS;
            final Session session = SESSIONS.computeIfAbsent(crew.level().getServer(), _ -> new Session());
            if (level.getBlockState(position).is(ShipContent.STORAGE_LOCKER) && player.isShiftKeyDown()) {
                claimSupplies(crew);
                return InteractionResult.SUCCESS;
            }
            if (position.equals(WIRE) && state(crew.level().getServer()).fault(Room.ENGINEERING) == Fault.POWER
                    && player.getItemInHand(hand).is(RepairContent.WIRE_KIT)) {
                if (!repairRole(crew, false)) return InteractionResult.FAIL;
                if (state(crew.level().getServer()).repairWire(Room.ENGINEERING)) {
                    player.getItemInHand(hand).shrink(1);
                    crew.sendSystemMessage(Component.literal("Cable connection replaced. Engineering power and automatic door control restored."));
                }
                return InteractionResult.SUCCESS;
            }
            if (ShipMap.task(level.getBlockState(position).getBlock()) != null) return InteractionResult.PASS;
            final BlockPos placed = position.relative(hit.getDirection());
            if (player.getItemInHand(hand).is(RepairContent.PATCH_PLATE.asItem())) {
                if (session.original == null || state(crew.level().getServer()).fault(Room.SCIENCE) != Fault.BREACH) {
                    crew.sendSystemMessage(Component.literal("No active hull breach needs a patch. Keep the crew's plates in storage."));
                    return InteractionResult.FAIL;
                }
                if (!placed.equals(BREACH) || player.distanceToSqr(Vec3.atCenterOf(BREACH)) > 36) {
                    crew.sendSystemMessage(Component.literal("Fit the patch to the marked hull breach at " + BREACH.toShortString() + "."));
                    return InteractionResult.FAIL;
                }
                if (!repairRole(crew, true) || !level.getBlockState(BREACH).isAir()) return InteractionResult.FAIL;
                level.setBlock(BREACH, RepairContent.PATCH_PLATE.defaultBlockState(), Block.UPDATE_ALL);
                player.getItemInHand(hand).shrink(1);
                state(crew.level().getServer()).patch(Room.SCIENCE);
                crew.sendSystemMessage(Component.literal("Hull patch secured. The leak stopped; wait for pressure to recover before reopening the room."));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean operator(final ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static boolean repairRole(final ServerPlayer player, final boolean hull) {
        final MoonMission.Role role = ShipController.get(player.level().getServer()).role(player);
        if (operator(player) || role == MoonMission.Role.MAINTENANCE || role == (hull ? MoonMission.Role.LIFE_SUPPORT : MoonMission.Role.POWER)) return true;
        player.sendSystemMessage(Component.literal(hull ? "Ask maintenance or life support to fit the hull patch." : "Ask maintenance or power to replace the cable connection."));
        return false;
    }

    public static void claimSupplies(final ServerPlayer player) {
        if (!ShipController.get(player.level().getServer()).selected() || !player.level().dimension().equals(ShipMap.DIMENSION)
                || player.distanceToSqr(Vec3.atCenterOf(ShipMap.target(ShipTask.STORAGE, player.blockPosition()))) > 36) {
            player.sendSystemMessage(Component.literal("Visit the ship's storage room during a selected mission to collect repair stores."));
            return;
        }
        final Session session = SESSIONS.computeIfAbsent(player.level().getServer(), _ -> new Session());
        if (!operator(player) && ShipController.get(player.level().getServer()).role(player) == null) {
            player.sendSystemMessage(Component.literal("Choose a crew role before collecting repair stores."));
            return;
        }
        if (session.suppliesClaimed) {
            player.sendSystemMessage(Component.literal("The crew's repair stores were already issued. Share the four plates and two wire kits."));
            return;
        }
        session.suppliesClaimed = true;
        for (final ItemStack stack : new ItemStack[] { new ItemStack(RepairContent.PATCH_PLATE, 4), new ItemStack(RepairContent.WIRE_KIT, 2) })
            if (!player.addItem(stack)) player.drop(stack, false, Prediction.SERVER_ONLY);
        player.sendSystemMessage(Component.literal("Crew repair stores issued: four hull plates and two wire kits. Share them with your specialists."));
    }

    public static void trigger(final MinecraftServer server, final String roomName, final String faultName) {
        if (!ShipController.get(server).selected() || ShipController.get(server).mission().snapshot().phase().ended())
            throw new IllegalStateException("Select a running ship attempt before scheduling incidents.");
        final Room room = Room.valueOf(roomName.toUpperCase(Locale.ROOT));
        final Fault fault = Fault.valueOf(faultName.toUpperCase(Locale.ROOT));
        final Session session = SESSIONS.computeIfAbsent(server, _ -> new Session());
        final ServerLevel level = server.getLevel(ShipMap.DIMENSION);
        if (level == null) throw new IllegalStateException("Prepare the ship before scheduling a room incident.");
        if (fault == Fault.BREACH) {
            if (room != Room.SCIENCE) throw new IllegalStateException("The first authored impact is in the science room.");
            if (session.state.fault(room) == Fault.BREACH) return;
            if (session.original != null) {
                final BlockState current = level.getBlockState(BREACH);
                if (!current.isAir() && !current.is(RepairContent.PATCH_PLATE) && !current.equals(session.original))
                    throw new IllegalStateException("Breach stopped: a changed hull cell was preserved.");
                level.setBlock(BREACH, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else {
                final BlockState original = level.getBlockState(BREACH);
                if (!original.is(Blocks.CONCRETE.gray()) && !original.is(ScifiBlocks.HULL_PANEL))
                    throw new IllegalStateException("Breach stopped: the authored hull cell was changed. Your blocks were preserved.");
                if (!level.getBlockState(BREACH.west()).isAir() || !level.getBlockState(BREACH.west(2)).isAir())
                    throw new IllegalStateException("Breach stopped: the interior approach was changed. Your blocks were preserved.");
                try {
                    final Path marker = breachMarker(server);
                    Files.createDirectories(marker.getParent());
                    Files.writeString(marker, BlockStateParser.serialize(original), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (final IOException error) {
                    throw new IllegalStateException("Breach stopped: cannot safely record the owned hull cell. " + error.getMessage(), error);
                }
                session.original = original;
                level.setBlock(BREACH, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else if (fault == Fault.POWER && room != Room.ENGINEERING)
            throw new IllegalStateException("The first authored cable failure is in engineering.");
        session.state.trigger(room, fault);
    }

    public static void tick(final MinecraftServer server, final boolean paused) {
        final Session session = SESSIONS.computeIfAbsent(server, _ -> new Session());
        session.state.tickSecond(paused);
        final ServerLevel level = server.getLevel(ShipMap.DIMENSION);
        if (level == null) return;
        if (!paused && !session.state.powered(Room.ENGINEERING))
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, WIRE.getX() + 0.5, WIRE.getY() + 0.6, WIRE.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.03);
        for (final ShipMap.Marker marker : ShipMap.MARKERS) {
            final Room room = roomAt(marker.position());
            if (room == null || session.state.powered(room)) continue;
            final BlockState block = level.getBlockState(marker.position());
            if (block.is(ShipMap.block(marker.task())))
                level.setBlock(marker.position(), block.setValue(BlockStateProperties.POWERED, false), Block.UPDATE_CLIENTS);
        }
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(ShipMap.DIMENSION)) continue;
            final Room room = roomAt(player.blockPosition());
            if (!paused && room != null && session.state.pressure(room) < 40) {
                player.sendOverlayMessage(Component.literal("LOW PRESSURE: " + label(room) + " " + session.state.pressure(room) + "/100. Wear the full suit or evacuate."));
                if (session.state.pressure(room) <= 20 && ShipController.get(server).mission() != null
                        && ShipController.get(server).mission().snapshot().phase() == ShipMission.Phase.ACTIVE
                        && !(player.getItemBySlot(EquipmentSlot.HEAD).is(ModContent.SPACE_HELMET)
                        && player.getItemBySlot(EquipmentSlot.CHEST).is(ModContent.SPACE_CHESTPLATE)
                        && player.getItemBySlot(EquipmentSlot.LEGS).is(ModContent.SPACE_LEGGINGS)
                        && player.getItemBySlot(EquipmentSlot.FEET).is(ModContent.SPACE_BOOTS)))
                    player.hurtServer(level, level.damageSources().drown(), 1);
            }
            send(player, false);
        }
    }

    public static boolean blockedStation(final ServerPlayer player, final BlockPos position) {
        if (!player.level().dimension().equals(ShipMap.DIMENSION)) return false;
        final Room room = roomAt(position);
        return room != null && (!state(player.level().getServer()).powered(room) || state(player.level().getServer()).pressure(room) < 40);
    }

    private static List<Room> doorRooms(final BlockPos position) {
        final List<Room> rooms = new ArrayList<>();
        for (final var entry : BOUNDS.entrySet()) {
            final AABB box = entry.getValue();
            if (position.getY() < box.minY || position.getY() >= box.maxY) continue;
            final double dx = Math.max(Math.max(box.minX - position.getX(), position.getX() - box.maxX), 0);
            final double dz = Math.max(Math.max(box.minZ - position.getZ(), position.getZ() - box.maxZ), 0);
            final double dy = Math.max(Math.max(box.minY - position.getY(), position.getY() - box.maxY), 0);
            if (dx * dx + dy * dy + dz * dz <= 2.25)
                rooms.add(entry.getKey());
        }
        return rooms;
    }

    public static @Nullable Boolean doorOverride(final ServerLevel level, final BlockPos position) {
        if (!level.dimension().equals(ShipMap.DIMENSION)) return null;
        final List<Room> rooms = doorRooms(position);
        if (rooms.isEmpty()) return null;
        final Session session = SESSIONS.computeIfAbsent(level.getServer(), _ -> new Session());
        final Integer crank = session.cranks.get(position);
        if (crank != null && crank >= level.getServer().getTickCount()) return true;
        if (rooms.stream().anyMatch(room -> !session.state.mayOpen(room))) return false;
        if (rooms.stream().anyMatch(room -> session.state.doorMode(room) == DoorMode.OPEN)) return true;
        return null;
    }

    public static boolean interactDoor(final ServerPlayer player, final BlockPos position) {
        if (!player.level().dimension().equals(ShipMap.DIMENSION) || player.distanceToSqr(Vec3.atCenterOf(position)) > 36) return false;
        if (doorRooms(position).isEmpty()) return false;
        final BlockState door = player.level().getBlockState(position);
        if (!(door.getBlock() instanceof DoorBlock)) return false;
        final BlockPos lower = door.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
        final Session session = SESSIONS.computeIfAbsent(player.level().getServer(), _ -> new Session());
        session.cranks.put(lower, player.level().getServer().getTickCount() + 100);
        for (final Direction direction : new Direction[] { door.getValue(DoorBlock.FACING).getClockWise(), door.getValue(DoorBlock.FACING).getCounterClockWise() }) {
            final BlockPos neighbor = lower.relative(direction);
            final BlockState adjacent = player.level().getBlockState(neighbor);
            if (adjacent.getBlock() instanceof DoorBlock && adjacent.getValue(DoorBlock.FACING) == door.getValue(DoorBlock.FACING))
                session.cranks.put(neighbor, player.level().getServer().getTickCount() + 100);
        }
        player.sendSystemMessage(Component.literal("Emergency crank: passage released for five seconds. Close room doors again after evacuating."));
        return true;
    }

    public static boolean hasUnresolved(final MinecraftServer server) {
        return state(server).hasUnresolved();
    }

    public static String status(final MinecraftServer server) {
        final ShipRoomState rooms = state(server);
        final StringBuilder text = new StringBuilder();
        for (final Room room : Room.values()) {
            if (rooms.fault(room) == Fault.NONE && rooms.pressure(room) >= 80) continue;
            if (!text.isEmpty()) text.append(" | ");
            text.append(label(room)).append(": ").append(rooms.fault(room)).append(" / ").append(rooms.pressure(room));
        }
        return text.isEmpty() ? "All compartments nominal" : text.toString();
    }

    private static String label(final Room room) {
        return room.name().replace('_', ' ').toLowerCase();
    }

    public static void send(final ServerPlayer player, final boolean open) {
        if (!ServerPlayNetworking.canSend(player, RoomStatusPayload.TYPE)) return;
        final MinecraftServer server = player.level().getServer();
        final Room current = roomAt(player.blockPosition());
        final JsonObject data = new JsonObject();
        data.addProperty("currentRoom", current == null ? "CORRIDOR" : current.name());
        data.addProperty("operator", operator(player));
        data.addProperty("alert", status(server));
        data.addProperty("instruction", hasUnresolved(server) ? "Shift-right-click storage for repair stores. Wire kit: engineering " + WIRE.toShortString()
                + ". Fit a plate into the science hull hole " + BREACH.toShortString() + "." : "Room doors can be closed, opened or returned to automatic control.");
        final JsonArray rooms = new JsonArray();
        for (final Room room : Room.values()) {
            final JsonObject item = new JsonObject();
            item.addProperty("id", room.name());
            item.addProperty("name", label(room));
            item.addProperty("pressure", state(server).pressure(room));
            item.addProperty("powered", state(server).powered(room));
            item.addProperty("fault", state(server).fault(room).name());
            item.addProperty("mode", state(server).doorMode(room).name());
            rooms.add(item);
        }
        data.add("rooms", rooms);
        ServerPlayNetworking.send(player, new RoomStatusPayload(data.toString(), open));
    }

    public static void clear(final MinecraftServer server) {
        final Session session = SESSIONS.remove(server);
        if (session == null) return;
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                final ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(RepairContent.WIRE_KIT) || stack.is(RepairContent.PATCH_PLATE.asItem()))
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        if (session.original == null) return;
        final ServerLevel level = server.getLevel(ShipMap.DIMENSION);
        if (level == null) return;
        final BlockState current = level.getBlockState(BREACH);
        if (current.isAir() || current.is(RepairContent.PATCH_PLATE)) {
            level.setBlock(BREACH, session.original, Block.UPDATE_ALL);
            try {
                Files.deleteIfExists(breachMarker(server));
            } catch (final IOException error) {
                NimbusNovaMod.LOGGER.warn("Owned breach restored; recovery marker could not be removed", error);
            }
        }
    }

    private static Path breachMarker(final MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/ship-breach-cell.txt");
    }

    public static void recoverOwnedBreach(final MinecraftServer server) {
        final Path marker = breachMarker(server);
        if (!Files.exists(marker)) return;
        final ServerLevel level = server.getLevel(ShipMap.DIMENSION);
        if (level == null) return;
        try {
            if (Files.size(marker) > 1024) throw new IOException("Invalid owned breach marker");
            final BlockState original = BlockStateParser.parseForBlock(server.registryAccess().lookupOrThrow(Registries.BLOCK),
                    Files.readString(marker), false).blockState();
            if (!original.is(Blocks.CONCRETE.gray()) && !original.is(ScifiBlocks.HULL_PANEL))
                throw new IOException("Unexpected owned hull material");
            final BlockState current = level.getBlockState(BREACH);
            if (!current.isAir() && !current.is(RepairContent.PATCH_PLATE) && !current.equals(original)) {
                NimbusNovaMod.LOGGER.warn("Breach recovery preserved a changed player block at {}", BREACH);
                return;
            }
            level.setBlock(BREACH, original, Block.UPDATE_ALL);
            Files.delete(marker);
            SESSIONS.remove(server);
        } catch (final IOException | CommandSyntaxException error) {
            NimbusNovaMod.LOGGER.warn("Cannot recover the owned breach cell; existing blocks were preserved", error);
        }
    }

    private static final class Session {
        private final ShipRoomState state = new ShipRoomState();
        private final Map<BlockPos, Integer> cranks = new HashMap<>();
        private BlockState original;
        private boolean suppliesClaimed;
    }
}
