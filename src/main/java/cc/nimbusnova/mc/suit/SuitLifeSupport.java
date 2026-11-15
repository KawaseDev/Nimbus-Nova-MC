package cc.nimbusnova.mc.suit;

import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.content.ScifiBlocks;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.world.EntranceAirlock;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.ship.ShipMission;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public final class SuitLifeSupport {
    // shortcut: air resets per visit; persist reserves when saved expeditions become part of a mission.
    private static final Map<MinecraftServer, ServerState> SERVERS = new IdentityHashMap<>();

    private SuitLifeSupport() {
        /* w */
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(SuitAirPayload.TYPE, SuitAirPayload.CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            final ServerState state = SERVERS.get(server);
            if (state != null)
                state.players.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            final ServerState state = SERVERS.computeIfAbsent(server, _ -> new ServerState());
            if (++state.ticks < 20) return;
            state.ticks = 0;
            for (final ServerPlayer player : server.getPlayerList().getPlayers())
                tickPlayer(player);
        });
    }

    public static void tickPlayer(final ServerPlayer player) {
        final MinecraftServer server = player.level().getServer();
        final ServerState state = SERVERS.computeIfAbsent(server, _ -> new ServerState());
        final boolean moon = player.level().dimension().equals(LunarSurface.DIMENSION);
        if ((!moon && !player.level().dimension().equals(MarsTrainingMap.DIMENSION)) || !player.isAlive() || player.isSpectator()) {
            state.players.remove(player.getUUID());
            return;
        }

        final SuitAirModel model = state.players.computeIfAbsent(player.getUUID(), _ -> new SuitAirModel());
        final MissionController controller = MissionController.get(server);
        final MissionState.Phase phase = controller.snapshot().phase();
        final ShipMission ship = moon ? ShipController.get(server).mission() : null;
        final boolean frozen = moon ? ship == null || ship.snapshot().phase() != ShipMission.Phase.ACTIVE
                : phase == MissionState.Phase.PAUSED || phase.ended() || controller.education().incident() != null;
        final boolean inside = !moon && pressurized(player.level(), player.blockPosition());
        final boolean helmet = player.getItemBySlot(EquipmentSlot.HEAD).is(ModContent.SPACE_HELMET);
        final boolean sealed = helmet && player.getItemBySlot(EquipmentSlot.CHEST).is(ModContent.SPACE_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModContent.SPACE_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModContent.SPACE_BOOTS);
        model.tick(inside, sealed, frozen);
        if (!frozen && !inside && model.air == 0 && (moon || phase == MissionState.Phase.ACTIVE))
            player.hurtServer(player.level(), player.level().damageSources().drown(), 1);

        ServerPlayNetworking.send(player, new SuitAirPayload(model.air, inside, sealed, helmet, frozen));
    }

    public static boolean pressurized(final BlockPos position) {
        return SuitAirModel.insideDome(position.getX(), position.getY(), position.getZ())
                || SuitAirModel.insideRoom(position.getX(), position.getY(), position.getZ());
    }

    public static boolean pressurized(final ServerLevel level, final BlockPos position) {
        if (EntranceAirlock.isChamber(position)) return EntranceAirlock.pressurized(level, position);
        if (SuitAirModel.insideRoom(position.getX(), position.getY(), position.getZ())) return true;
        if (!SuitAirModel.insideDome(position.getX(), position.getY(), position.getZ())) return false;

        final var crown = level.getBlockState(new BlockPos(2, 93, 0));
        return crown.is(ScifiBlocks.SUPPORT_BEAM) || crown.is(ScifiBlocks.DOME_GLASS) || crown.is(ScifiBlocks.FLOOR_LIGHT);
    }

    private static final class ServerState {
        private int ticks;
        private final Map<UUID, SuitAirModel> players = new HashMap<>();
    }
}
