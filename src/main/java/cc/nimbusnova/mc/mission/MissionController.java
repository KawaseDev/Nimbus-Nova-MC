package cc.nimbusnova.mc.mission;

import cc.nimbusnova.mc.content.ModBlocks;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import cc.nimbusnova.mc.NimbusNovaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MissionController {
    private static final Map<MinecraftServer, MissionController> SESSIONS = new IdentityHashMap<>();

    // shortcut: one transient mission per server, add session persistence when the backend integrates.
    // Each reset draws one of the real storms so repeat attempts differ, unless Mission Control pins one.
    private DustStorm pinnedStorm;
    private MissionState mission = new MissionState(nextStorm());
    private final Map<UUID, CrewRole> roles = new HashMap<>();
    private final Map<GlobalPos, Station> stations = new HashMap<>();
    private int ticks;
    private MissionState.Phase lastBroadcastPhase = MissionState.Phase.READY;

    public static MissionController get(final MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, ignored -> new MissionController());
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(MissionSnapshotPayload.TYPE, MissionSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenConsolePayload.TYPE, OpenConsolePayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> get(server).loadStations(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.JOIN.register((_, _, server) -> get(server).broadcast(server));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            final MissionController controller = get(serverPlayer.level().getServer());
            final Station station = controller.stations.get(GlobalPos.of(level.dimension(), hit.getBlockPos()));
            if (station == null) return InteractionResult.PASS;

            if (station == Station.CANISTER) {
                controller.replenishOxygen(serverPlayer);
                return InteractionResult.SUCCESS;
            }

            controller.setEquipment(serverPlayer.createCommandSourceStack(), station.equipment,
                    station == Station.OXYGEN ? !controller.mission.snapshot().oxygenOnline()
                            : !controller.mission.snapshot().researchOnline());
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            final MissionController controller = get(server);
            if (++controller.ticks < 20) return;

            controller.ticks = 0;
            if (server.getPlayerList().getPlayerCount() == 0 && controller.mission.snapshot().phase() == MissionState.Phase.ACTIVE)
                controller.mission.pause();

            controller.mission.tickSecond();
            controller.broadcast(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mission")
                    .executes(context -> {
                        final CommandSourceStack source = context.getSource();
                        final MissionState.Snapshot state = get(source.getServer()).mission.snapshot();
                        source.sendSuccess(() -> Component.literal("Nimbus Nova / " + state.phase()
                                + " / battery " + Math.round(state.battery()) + "% / oxygen " + Math.round(state.oxygen())
                                + "% / use J for the mission console"), false);
                        return 1;
                    });

            for (final String action : new String[] { "start", "pause", "resume", "reset", "abort" }) {
                root.then(Commands.literal(action).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> get(context.getSource().getServer()).control(context.getSource(), action)));
            }

            final LiteralArgumentBuilder<CommandSourceStack> role = Commands.literal("role");
            for (final CrewRole crewRole : CrewRole.values()) {
                role.then(Commands.literal(crewRole.command).executes(context -> {
                    final CommandSourceStack source = context.getSource();
                    return get(source.getServer()).assign(source, source.getPlayerOrException(), crewRole);
                }));
            }

            root.then(role);
            final LiteralArgumentBuilder<CommandSourceStack> storms = Commands.literal("storm")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
            for (final DustStorm storm : DustStorm.values())
                storms.then(Commands.literal(storm.id).executes(context -> get(context.getSource().getServer()).pinStorm(context.getSource(), storm)));

            storms.then(Commands.literal("random").executes(context -> get(context.getSource().getServer()).pinStorm(context.getSource(), null)));
            root.then(storms);
            root.then(Commands.literal("forecast").executes(context -> get(context.getSource().getServer()).forecast(context.getSource())));
            final var assign = Commands.argument("player", EntityArgument.player());
            for (final CrewRole crewRole : CrewRole.values()) {
                assign.then(Commands.literal(crewRole.command).executes(context -> {
                    final CommandSourceStack source = context.getSource();
                    return get(source.getServer()).assign(source, EntityArgument.getPlayer(context, "player"), crewRole);
                }));
            }

            root.then(Commands.literal("assign").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(assign));
            final LiteralArgumentBuilder<CommandSourceStack> equipment = Commands.literal("equipment");
            final LiteralArgumentBuilder<CommandSourceStack> station = Commands.literal("station")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
            for (final Station type : Station.values()) {
                station.then(Commands.literal(type.name().toLowerCase(Locale.ROOT)).executes(context -> {
                    final CommandSourceStack source = context.getSource();
                    final ServerPlayer player = source.getPlayerOrException();
                    if (!(player.pick(5, 0, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                        source.sendFailure(Component.literal("Look at a block within five blocks to bind a station."));
                        return 0;
                    }

                    final MissionController controller = get(source.getServer());
                    controller.stations.put(GlobalPos.of(player.level().dimension(), hit.getBlockPos()), type);
                    controller.saveStations(source.getServer());
                    source.sendSuccess(() -> Component.literal("Station bound: " + type.name().toLowerCase(Locale.ROOT)
                            + (type == Station.CANISTER ? ". The life-support engineer can right-click it to use an oxygen canister."
                            : ". Its engineer can right-click this block to toggle the system.") + " No block was replaced."), false);
                    return 1;
                }));
            }

            station.then(Commands.literal("clear").executes(context -> {
                final CommandSourceStack source = context.getSource();
                final MissionController controller = get(source.getServer());
                final int removed = controller.stations.size();
                controller.stations.clear();
                controller.saveStations(source.getServer());
                source.sendSuccess(() -> Component.literal("Removed " + removed + " station bindings. No blocks were changed."), false);
                return 1;
            }));

            for (final MissionState.Equipment system : MissionState.Equipment.values()) {
                equipment.then(Commands.literal(system.name().toLowerCase(Locale.ROOT))
                        .then(Commands.argument("online", BoolArgumentType.bool()).executes(context -> {
                            final CommandSourceStack source = context.getSource();
                            return get(source.getServer()).setEquipment(source, system, BoolArgumentType.getBool(context, "online"));
                        })));
            }

            dispatcher.register(root.then(equipment).then(station));
        });
    }

    // Station bindings live in the world folder so the demo map survives a server restart.
    private static Path stationFile(final MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("nimbus_nova_stations.txt");
    }

    private void loadStations(final MinecraftServer server) {
        final Path file = stationFile(server);
        if (!Files.exists(file)) return;

        try {
            for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                final String[] parts = line.trim().split(" ");
                if (parts.length != 5) continue;

                try {
                    final ResourceKey<Level> dimension =
                            ResourceKey.create(Registries.DIMENSION, Identifier.parse(parts[0]));
                    stations.put(GlobalPos.of(dimension, new BlockPos(Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))), Station.valueOf(parts[4]));
                } catch (final RuntimeException exception) {
                    NimbusNovaMod.LOGGER.warn("Skipping unreadable station line '{}'", line);
                }
            }

            NimbusNovaMod.LOGGER.info("Loaded {} mission stations", stations.size());
        } catch (final IOException exception) {
            NimbusNovaMod.LOGGER.warn("Could not read mission stations from {}", file, exception);
        }
    }

    private void saveStations(final MinecraftServer server) {
        final List<String> lines = new ArrayList<>();
        stations.forEach((position, station) -> lines.add(position.dimension().identifier() + " " + position.pos().getX() + " "
                + position.pos().getY() + " " + position.pos().getZ() + " " + station.name()));
        try {
            Files.write(stationFile(server), lines, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            NimbusNovaMod.LOGGER.warn("Could not save mission stations", exception);
        }
    }

    public void bindStation(final GlobalPos position, final Station station) {
        stations.put(position, station);
    }

    public void unbindStation(final GlobalPos position) {
        stations.remove(position);
    }

    public MissionState.Snapshot snapshot() {
        return mission.snapshot();
    }

    private int setEquipment(final CommandSourceStack source, final MissionState.Equipment system, final boolean online) {
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                && !(source.getEntity() instanceof ServerPlayer player
                && roles.getOrDefault(player.getUUID(), CrewRole.OBSERVER).canControl(system))) {
            source.sendFailure(Component.literal("Your role cannot control this system. Ask its engineer."));
            return 0;
        }

        try {
            mission.setEquipment(system, online);
            broadcast(source.getServer());
            return 1;
        } catch (final IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private DustStorm nextStorm() {
        if (pinnedStorm != null) return pinnedStorm;

        final DustStorm[] storms = DustStorm.values();
        return storms[ThreadLocalRandom.current().nextInt(storms.length)];
    }

    private int pinStorm(final CommandSourceStack source, final DustStorm storm) {
        pinnedStorm = storm;
        if (mission.snapshot().phase() == MissionState.Phase.READY)
            mission = new MissionState(nextStorm());

        broadcast(source.getServer());
        source.sendSuccess(() -> Component.literal(storm == null
                ? "Each reset now picks a random real storm. Current: " + mission.snapshot().dustStorm().label
                : "Next missions use the " + storm.label + "."), false);
        return 1;
    }

    private int forecast(final CommandSourceStack source) {
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                && !(source.getEntity() instanceof ServerPlayer player
                && roles.getOrDefault(player.getUUID(), CrewRole.OBSERVER).seesWeather())) {
            source.sendFailure(Component.literal("Only the weather officer can read the sky sensors. Ask them for a forecast."));
            return 0;
        }

        final MissionState.Snapshot state = mission.snapshot();
        if (state.phase() != MissionState.Phase.ACTIVE) {
            source.sendFailure(Component.literal("Forecasts are available while the mission is running."));
            return 0;
        }

        final Component message = Component.literal("[Weather] " + DustStormProfile.forecast(state.dustStorm(), state.elapsedSeconds()));
        for (final ServerPlayer crew : source.getServer().getPlayerList().getPlayers())
            crew.sendSystemMessage(message);

        return 1;
    }

    public boolean replenishOxygen(final ServerPlayer player) {
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                && roles.getOrDefault(player.getUUID(), CrewRole.OBSERVER) != CrewRole.LIFE_SUPPORT) {
            player.sendSystemMessage(Component.literal("Ask the life-support engineer to use this canister."));
            return false;
        }

        if (!mission.replenishOxygen()) {
            player.sendSystemMessage(Component.literal(mission.snapshot().canistersLeft() == 0
                    ? "No oxygen canisters left. Keep oxygen production running."
                    : "Canisters require a running mission with space in the oxygen reserve."));
            return false;
        }

        broadcast(player.level().getServer());
        return true;
    }

    public void openConsole(final ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, OpenConsolePayload.TYPE)) return;

        broadcast(player.level().getServer());
        ServerPlayNetworking.send(player, new OpenConsolePayload());
    }

    public int control(final CommandSourceStack source, final String action) {
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            source.sendFailure(Component.literal("Only Mission Control can change the mission phase."));
            return 0;
        }

        try {
            switch (action) {
                case "start" -> {
                    if (source.getServer().getPlayerList().getPlayerCount() == 0)
                        throw new IllegalStateException("A crew member must join before the mission starts.");

                    mission.start();
                }
                case "pause" -> mission.pause();
                case "resume" -> mission.resume();
                case "abort" -> mission.abort();
                case "reset" -> mission = new MissionState(nextStorm());
                default -> throw new IllegalArgumentException("Unknown mission action: " + action);
            }

            ticks = 0;
            broadcast(source.getServer());
            source.sendSuccess(() -> Component.literal("Mission: " + action), false);
            return 1;
        } catch (final IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private int assign(final CommandSourceStack source, final ServerPlayer player, final CrewRole role) {
        if (mission.snapshot().phase() != MissionState.Phase.READY && mission.snapshot().phase() != MissionState.Phase.PAUSED) {
            source.sendFailure(Component.literal("Choose roles before starting, or ask Mission Control to pause first."));
            return 0;
        }

        if (mission.snapshot().phase() == MissionState.Phase.PAUSED
                && !source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            source.sendFailure(Component.literal("Only Mission Control can reassign roles during a paused mission."));
            return 0;
        }

        roles.put(player.getUUID(), role);
        broadcast(source.getServer());
        source.sendSuccess(() -> Component.literal(player.getName().getString() + " assigned: " + role.label), false);
        return 1;
    }

    public void broadcast(final MinecraftServer server) {
        final MissionState.Snapshot state = mission.snapshot();
        // The debrief opens once for everyone when the mission ends.
        final boolean justEnded = state.phase().ended() && !lastBroadcastPhase.ended();
        lastBroadcastPhase = state.phase();
        for (final var station : stations.entrySet()) {
            final var level = server.getLevel(station.getKey().dimension());
            if (level == null || !level.getChunkSource().hasChunk(station.getKey().pos().getX() >> 4, station.getKey().pos().getZ() >> 4) || station.getValue() == Station.CANISTER) continue;

            ModBlocks.updateState(level, station.getKey().pos(), state.battery() > 0
                    && (station.getValue() == Station.OXYGEN ? state.oxygenOnline() : state.researchOnline()));
        }


        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, MissionSnapshotPayload.TYPE)) continue;

            ServerPlayNetworking.send(player, new MissionSnapshotPayload(state,
                    roles.getOrDefault(player.getUUID(), CrewRole.OBSERVER),
                    player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER),
                    server.getPlayerList().getPlayerCount()));
            if (justEnded && ServerPlayNetworking.canSend(player, OpenConsolePayload.TYPE))
                ServerPlayNetworking.send(player, new OpenConsolePayload());
        }
    }
}
