package cc.nimbusnova.mc.mission;

import cc.nimbusnova.mc.report.ReportStore;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.learning.LearningChecks;
import cc.nimbusnova.mc.scenario.OutpostScenarioController;
import cc.nimbusnova.mc.world.ship.ShipMap;

import cc.nimbusnova.mc.content.ModBlocks;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.util.Prediction;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
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
    private MissionPreparation preparation = new MissionPreparation();
    private final EducationPause education = new EducationPause();
    private MissionState mission = newPreparedMission();
    private final Map<UUID, MissionPreparation.TaskKind> openTasks = new HashMap<>();
    private String progressFeedback = "Welcome, crew! Follow the mission marker to Commander Vega.";
    private final Map<UUID, CrewRole> roles = new HashMap<>();
    private final Map<GlobalPos, Station> stations = new HashMap<>();
    private int ticks;
    private MissionState.Phase lastBroadcastPhase = MissionState.Phase.READY;

    /** Decides whether a player is outside the shelter; null means the player is not at the outpost. */
    @FunctionalInterface
    public interface ShelterCheck {
        Boolean exposed(ServerPlayer player);
    }

    private static ShelterCheck shelterCheck = _ -> null;

    public static void setShelterCheck(final ShelterCheck check) {
        shelterCheck = check;
    }

    public static MissionController get(final MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, ignored -> new MissionController());
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(MissionSnapshotPayload.TYPE, MissionSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenConsolePayload.TYPE, OpenConsolePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MissionProgressPayload.TYPE, MissionProgressPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MissionTaskOpenPayload.TYPE, MissionTaskOpenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MissionTaskActionPayload.TYPE, MissionTaskActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MissionTaskActionPayload.TYPE, (packet, context) ->
                context.server().execute(() -> get(context.server()).taskAction(context.player(), packet)));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> get(server).loadStations(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.JOIN.register((_, _, server) -> get(server).broadcast(server));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            final MissionController controller = get(serverPlayer.level().getServer());
            final Station station = controller.stations.get(GlobalPos.of(level.dimension(), hit.getBlockPos()));
            if (station == null) return InteractionResult.PASS;

            if (station != Station.CANISTER && (controller.mission.snapshot().phase() == MissionState.Phase.READY
                    || station == Station.RESEARCH && controller.mission.relayFault())) {
                controller.interaction(serverPlayer, station == Station.RESEARCH
                        ? MissionPreparation.TaskKind.POWER : MissionPreparation.TaskKind.OXYGEN);
                return InteractionResult.SUCCESS;
            }

            if (station == Station.CANISTER) {
                controller.replenishOxygen(serverPlayer);
                return InteractionResult.SUCCESS;
            }

            if (station == Station.OXYGEN && (serverPlayer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                    || controller.role(serverPlayer.getUUID()).canControl(MissionState.Equipment.OXYGEN))) {
                if (ServerPlayNetworking.canSend(serverPlayer, OpenConsolePayload.TYPE))
                    ServerPlayNetworking.send(serverPlayer, new OpenConsolePayload(1));
                return InteractionResult.SUCCESS;
            }

            controller.setEquipment(serverPlayer.createCommandSourceStack(), station.equipment,
                    station == Station.OXYGEN ? !controller.mission.snapshot().oxygenOnline()
                            : !controller.mission.snapshot().researchOnline());
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            final MissionController controller = get(server);
            if (controller.mission.snapshot().phase() == MissionState.Phase.READY && controller.preparation.tickOxygen()) {
                final int stable = controller.preparation.puzzleAux(MissionPreparation.TaskKind.OXYGEN, false) >>> 16;
                if (stable % 10 == 0)
                    controller.sendOxygenTaskUpdates(server);
            }
            if (++controller.ticks < 20) return;

            controller.ticks = 0;
            if (server.getPlayerList().getPlayerCount() == 0 && controller.mission.snapshot().phase() == MissionState.Phase.ACTIVE)
                controller.mission.pause();

            controller.mission.tickSecond();
            final boolean learningPause = controller.education.afterTick(controller.mission);
            int exposed = 0;
            for (final ServerPlayer player : server.getPlayerList().getPlayers())
                if (Boolean.TRUE.equals(shelterCheck.exposed(player))) exposed++;

            controller.mission.recordExposure(exposed);
            controller.broadcast(server);
            if (learningPause) {
                for (final ServerPlayer player : server.getPlayerList().getPlayers())
                    if (ServerPlayNetworking.canSend(player, OpenConsolePayload.TYPE))
                        ServerPlayNetworking.send(player, new OpenConsolePayload(2));
            }
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

            for (final String action : new String[] { "start", "debug_start", "pause", "resume", "acknowledge", "reset", "abort" }) {
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

    public CrewRole role(final UUID player) {
        return roles.getOrDefault(player, CrewRole.OBSERVER);
    }

    public EducationPause education() { return education; }

    public MissionState mission() {
        return mission;
    }

    public Set<CrewRole> assignedRoles() {
        return Set.copyOf(roles.values());
    }

    public MissionState.Snapshot snapshot() {
        return mission.snapshot();
    }

    public boolean chargeVehicle(final ServerPlayer player) {
        if (!player.level().dimension().equals(MarsTrainingMap.DIMENSION)
                || player.distanceToSqr(28.5, 66, 25.5) > 64
                || !player.getMainHandItem().is(ModContent.ENGINEERING_TOOL)
                || role(player.getUUID()) != CrewRole.POWER && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(Component.literal("The power engineer connects the rover at its workshop using the engineering tool."));
            return false;
        }
        if (!mission.transferVehicleCharge()) {
            player.sendSystemMessage(Component.literal("Charging needs an unpaused mission and at least 5 shared energy units."));
            return false;
        }
        progressFeedback = mission.snapshot().lastEvent();
        broadcast(player.level().getServer());
        return true;
    }

    public int backupCellsRemaining() {
        return mission.backupCellsRemaining();
    }

    public boolean useBackupPower(final ServerPlayer player) {
        if (!mission.useBackupPower()) return false;

        progressFeedback = mission.snapshot().lastEvent();
        broadcast(player.level().getServer());
        return true;
    }

    public boolean cycleAirlock(final ServerPlayer player) {
        if (!mission.cycleAirlock()) return false;

        progressFeedback = mission.snapshot().lastEvent();
        broadcast(player.level().getServer());
        return true;
    }

    public boolean interaction(final ServerPlayer player, final MissionPreparation.TaskKind kind) {
        if (!atTask(player, kind)) {
            player.sendSystemMessage(Component.literal("Move close to the marked equipment at the Mars outpost first."));
            return false;
        }

        final boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        final CrewRole role = role(player.getUUID());
        if (!operator && !allowed(role, kind)) {
            player.sendSystemMessage(Component.literal("This is another crew member's task. Follow your own mission marker."));
            return false;
        }

        if (kind == MissionPreparation.TaskKind.WEATHER_SCAN) {
            forecast(player.createCommandSourceStack());
            return true;
        }

        if (kind == MissionPreparation.TaskKind.SUPPLIES) {
            try {
                final Path world = player.level().getServer().getWorldPath(LevelResource.ROOT);
                // shortcut: old claims can only be inferred from carried kit, unless a legacy claim record becomes available.
                final boolean existingKit = player.getInventory().contains(stack -> stack.is(ModContent.MISSION_TABLET)
                        || stack.is(ModContent.ENGINEERING_TOOL) || stack.is(ModContent.OXYGEN_CANISTER));
                final boolean first = GearClaims.claim(world, player.getUUID(), GearClaims.Kit.MARS);
                if (!first || existingKit) {
                    player.sendSystemMessage(Component.literal("You already collected your crew kit. Check your inventory."));
                    return false;
                }
            } catch (final IOException error) {
                NimbusNovaMod.LOGGER.error("Could not save the crew kit claim", error);
                player.sendSystemMessage(Component.literal("Could not save your kit claim. Ask the teacher to check the server."));
                return false;
            }

            for (final Item item : new Item[] { ModContent.MISSION_TABLET, ModContent.ENGINEERING_TOOL, ModContent.OXYGEN_CANISTER }) {
                final ItemStack stack = new ItemStack(item);
                if (!player.getInventory().add(stack))
                    player.drop(stack, false, Prediction.SERVER_ONLY);
            }

            final EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
            final Item[] suit = { ModContent.SPACE_HELMET, ModContent.SPACE_CHESTPLATE, ModContent.SPACE_LEGGINGS, ModContent.SPACE_BOOTS };
            for (int index = 0; index < slots.length; index++) {
                if (player.getItemBySlot(slots[index]).isEmpty())
                    player.setItemSlot(slots[index], new ItemStack(suit[index]));
            }
            player.sendSystemMessage(Component.literal("Crew kit collected and empty suit slots equipped. Follow your next mission marker."));
            broadcast(player.level().getServer());
            return true;
        }

        if (kind == MissionPreparation.TaskKind.POWER && mission.relayFault()) {
            if (mission.snapshot().phase() != MissionState.Phase.ACTIVE) return false;
            mission.setEquipment(MissionState.Equipment.RESEARCH, false);
            broadcast(player.level().getServer());
            openTask(player, kind, true);
            return true;
        }

        if (mission.snapshot().phase() != MissionState.Phase.READY) {
            player.sendSystemMessage(Component.literal("Preparation is complete. Follow the current mission objective."));
            return false;
        }

        if (kind == MissionPreparation.TaskKind.LAUNCH) {
            if (!operator) {
                player.sendSystemMessage(Component.literal("Crew ready! Ask Mission Control to begin the mission."));
                return false;
            }
            return control(player.createCommandSourceStack(), "start") == 1;
        }

        if (kind == MissionPreparation.TaskKind.BRIEFING) {
            preparation.briefing();
            progressFeedback = "Briefing received. Your role's next task is marked on your HUD.";
        } else if (!preparation.briefed()) {
            player.sendSystemMessage(Component.literal("Meet Commander Vega first. Follow the briefing marker."));
            return false;
        } else if (kind == MissionPreparation.TaskKind.POWER || kind == MissionPreparation.TaskKind.OXYGEN) {
            if (preparation.checked(kind)) {
                player.sendSystemMessage(Component.literal("This system is ready. Help the crew finish the remaining checks."));
                return false;
            }
            openTask(player, kind, false);
            return true;
        } else if (kind == MissionPreparation.TaskKind.COLLECT_SAMPLE) {
            if (!preparation.collectSample()) return false;
            progressFeedback = "Mars sample secured in shared crew cargo. Bring it to the habitat analyzer.";
        } else if (kind == MissionPreparation.TaskKind.DELIVER_SAMPLE) {
            if (!preparation.deliverSample()) {
                player.sendSystemMessage(Component.literal("Collect the marked Mars sample before returning it for analysis."));
                return false;
            }
            progressFeedback = "Sample delivered. Finish the equipment checks, then return to Commander Vega.";
        }

        broadcast(player.level().getServer());
        return true;
    }

    private void openTask(final ServerPlayer player, final MissionPreparation.TaskKind kind, final boolean relay) {
        if (!ServerPlayNetworking.canSend(player, MissionTaskOpenPayload.TYPE)) return;
        if (preparation.puzzleRevision(kind, relay) < 0)
            openTasks.remove(player.getUUID());
        else
            openTasks.put(player.getUUID(), kind);
        sendTaskState(player, kind, relay);
    }

    private void sendTaskState(final ServerPlayer player, final MissionPreparation.TaskKind kind, final boolean relay) {
        if (!ServerPlayNetworking.canSend(player, MissionTaskOpenPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new MissionTaskOpenPayload(kind, preparation.puzzleRevision(kind, relay),
                preparation.puzzleState(kind, relay), preparation.puzzleAux(kind, relay)));
    }

    private void sendOxygenTaskUpdates(final MinecraftServer server) {
        for (final var task : openTasks.entrySet()) {
            if (task.getValue() != MissionPreparation.TaskKind.OXYGEN) continue;
            final ServerPlayer player = server.getPlayerList().getPlayer(task.getKey());
            if (player != null && atTask(player, task.getValue()))
                sendTaskState(player, task.getValue(), false);
        }
    }

    private void taskAction(final ServerPlayer player, final MissionTaskActionPayload packet) {
        final MissionPreparation.TaskKind kind = packet.kind();
        if (kind != MissionPreparation.TaskKind.POWER && kind != MissionPreparation.TaskKind.OXYGEN) return;
        if (openTasks.get(player.getUUID()) != kind || !atTask(player, kind)) return;
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && !allowed(role(player.getUUID()), kind)) return;
        final boolean relay = kind == MissionPreparation.TaskKind.POWER && mission.relayFault();
        if (relay ? mission.snapshot().phase() != MissionState.Phase.ACTIVE
                : mission.snapshot().phase() != MissionState.Phase.READY) {
            sendTaskState(player, kind, relay);
            return;
        }

        final boolean accepted = relay ? preparation.repairStep(packet.step(), packet.choice())
                : preparation.step(kind, packet.step(), packet.choice());
        if (!accepted) {
            progressFeedback = kind == MissionPreparation.TaskKind.POWER ? "The circuit changed. Follow its updated cable connections."
                    : "Balance pressure and flow in the green bands, hold steady for two seconds, then activate.";
            broadcast(player.level().getServer());
            openTask(player, kind, relay);
            return;
        }

        if (preparation.puzzleRevision(kind, relay) < 0) {
            openTasks.remove(player.getUUID());
            if (relay) {
                mission.repairRelay();
                progressFeedback = "Relay repaired. Research remains off: decide with your crew when to restore it.";
            } else {
                mission.prepareEquipment(kind == MissionPreparation.TaskKind.POWER ? MissionState.Equipment.RESEARCH : MissionState.Equipment.OXYGEN, true);
                progressFeedback = kind == MissionPreparation.TaskKind.POWER ? "Circuit connected! Research equipment is available." : "Pressure and flow stable. The habitat recycler is ready.";
            }
        } else
            progressFeedback = kind == MissionPreparation.TaskKind.POWER ? "Route the lit cable from the battery to the research output."
                    : "The valves affect both gauges. Keep pressure and flow in their green bands.";

        broadcast(player.level().getServer());
        sendTaskState(player, kind, relay);
    }

    private static boolean allowed(final CrewRole role, final MissionPreparation.TaskKind kind) {
        return switch (kind) {
            case POWER -> role == CrewRole.POWER;
            case OXYGEN -> role == CrewRole.LIFE_SUPPORT;
            case WEATHER_SCAN -> role == CrewRole.WEATHER;
            case COLLECT_SAMPLE, DELIVER_SAMPLE -> role != CrewRole.OBSERVER;
            case BRIEFING, SUPPLIES -> true;
            case LAUNCH -> false;
        };
    }

    private static BlockPos taskPosition(final MissionPreparation.TaskKind kind) {
        return switch (kind) {
            case POWER -> new BlockPos(-24, 66, 0);
            case OXYGEN -> new BlockPos(-5, 66, 3);
            case COLLECT_SAMPLE -> new BlockPos(40, 66, -16);
            case DELIVER_SAMPLE -> new BlockPos(6, 66, 1);
            case SUPPLIES -> new BlockPos(7, 66, -3);
            case WEATHER_SCAN -> new BlockPos(14, 66, -4);
            default -> new BlockPos(-4, 66, -4);
        };
    }

    private static boolean atTask(final ServerPlayer player, final MissionPreparation.TaskKind kind) {
        if (!player.level().dimension().equals(MarsTrainingMap.DIMENSION)) return false;
        if (player.distanceToSqr(Vec3.atCenterOf(taskPosition(kind))) <= 36) return true;
        return kind == MissionPreparation.TaskKind.DELIVER_SAMPLE
                && player.distanceToSqr(Vec3.atCenterOf(taskPosition(MissionPreparation.TaskKind.BRIEFING))) <= 36;
    }

    public MissionProgressPayload progress(final ServerPlayer player) {
        final MissionState.Snapshot state = mission.snapshot();
        final boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        final CrewRole role = role(player.getUUID());
        if (!player.level().dimension().equals(MarsTrainingMap.DIMENSION))
            return progress("ARRIVAL", "Travel to Mars", "Ask Mission Control to prepare the outpost, then choose Travel to Mars.",
                    "Open the mission panel to travel", player.blockPosition(), preparation.completed(), 5, 0);

        if (state.phase().ended())
            return progress("COMPLETE", state.objective(), "Open the debrief to see what your crew achieved and try another strategy.", "Press J for the debrief", new BlockPos(0, 66, 0), state.elapsedSeconds(), 240, 0);

        if (state.phase() == MissionState.Phase.PAUSED)
            return progress("PAUSED", "Mission paused", "Your reserves and mission time are safe. Discuss the next decision with your crew.", "Wait for Mission Control", new BlockPos(0, 66, 0), state.elapsedSeconds(), 240, 0);

        if (state.phase() == MissionState.Phase.ACTIVE) {
            if (MissionState.solarWarning(state.elapsedSeconds()) || MissionState.solarStorm(state.elapsedSeconds())) {
                final boolean sheltered = Boolean.FALSE.equals(shelterCheck.exposed(player));
                final int untilClear = MissionState.SOLAR_END_SECONDS - state.elapsedSeconds();
                return progress(sheltered ? "SHELTER_SAFE" : "SHELTER", sheltered ? "Stay inside the shelter" : "Get to the crew shelter!",
                        sheltered ? "You are protected. Stay inside for " + untilClear + " seconds until the all-clear."
                                : MissionState.solarWarning(state.elapsedSeconds())
                                ? "Particles arrive in " + (MissionState.SOLAR_START_SECONDS - state.elapsedSeconds()) + " seconds. Follow the marker south and enter the shelter."
                                : "Get inside now! The solar particle event ends in " + untilClear + " seconds.",
                        sheltered ? "Stay here until all-clear" : "Walk through the shelter door", new BlockPos(0, 66, 25), state.elapsedSeconds(), 240, 0);
            }

            if (role == CrewRole.OBSERVER && !operator)
                return progress("ROLE", "Choose a crew role", "Open the Crew tab to pick a role and see your task.",
                        "Open the crew tablet", player.blockPosition(), state.elapsedSeconds(), 240, 0);

            if (mission.relayFault() && (operator || role == CrewRole.POWER))
                return taskProgress(MissionPreparation.TaskKind.POWER, "RELAY", "Repair the damaged power relay", "A relay has failed. At the marked console, reroute the circuit to make research available again.", state.elapsedSeconds(), 240, true);

            if (role == CrewRole.WEATHER && !operator)
                return taskProgress(MissionPreparation.TaskKind.WEATHER_SCAN, "FORECAST", "Send your crew a forecast", "Check the weather terminal and tell your crew how sunlight is changing.", state.elapsedSeconds(), 240, false);

            if (role == CrewRole.LIFE_SUPPORT && !operator)
                return taskProgress(MissionPreparation.TaskKind.OXYGEN, "OXYGEN", "Keep the habitat breathing", "Watch the oxygen reserve. Keep production on, and use an emergency canister only when needed.", state.elapsedSeconds(), 240, false);

            return taskProgress(MissionPreparation.TaskKind.POWER, "RESEARCH", "Balance research with survival",
                    state.researchOnline() ? "Research is running. Preserve reserves through the dust event and finish 90 seconds of science."
                            : "Research is off. When reserves allow, use the power console to resume science.", state.elapsedSeconds(), 240, false);
        }

        if (!preparation.briefed())
            return taskProgress(MissionPreparation.TaskKind.BRIEFING, "BRIEFING", "Meet Commander Vega", "Welcome to Mars! Walk to Commander Vega in the habitat and right-click to receive your mission.", preparation.completed(), 5, false);

        if (!operator && role == CrewRole.OBSERVER)
            return taskProgress(MissionPreparation.TaskKind.BRIEFING, "ROLE", "Choose your crew role", "Open the crew panel and choose Power, Life support or Weather. Your own task marker will appear.", preparation.completed(), 5, false);

        if (!hasCrewKitClaim(player))
            return taskProgress(MissionPreparation.TaskKind.SUPPLIES, "GEAR", "Collect your astronaut kit", "Visit the supply locker in the habitat. Right-click to collect tools and equip your empty suit slots.", preparation.completed(), 5, false);

        if (!preparation.checked(MissionPreparation.TaskKind.POWER) && (operator || role == CrewRole.POWER))
            return taskProgress(MissionPreparation.TaskKind.POWER, "POWER", "Bring the power relay online", "Go to the power yard. Rotate the cable tiles until electricity reaches the research output.", preparation.completed(), 5, false);

        if (!preparation.checked(MissionPreparation.TaskKind.OXYGEN) && (operator || role == CrewRole.LIFE_SUPPORT))
            return taskProgress(MissionPreparation.TaskKind.OXYGEN, "OXYGEN", "Check the oxygen recycler", "Go to the oxygen processor. Balance pressure and flow in both green bands, hold steady, then activate the pump.", preparation.completed(), 5, false);

        if (!preparation.checked(MissionPreparation.TaskKind.COLLECT_SAMPLE))
            return taskProgress(MissionPreparation.TaskKind.COLLECT_SAMPLE, "SAMPLE", "Collect the marked Mars sample", "Follow the marker east to the sampling site. Right-click the sample to secure it for the whole crew.", preparation.completed(), 5, false);

        if (preparation.checked(MissionPreparation.TaskKind.COLLECT_SAMPLE) && !preparation.checked(MissionPreparation.TaskKind.DELIVER_SAMPLE))
            return taskProgress(MissionPreparation.TaskKind.DELIVER_SAMPLE, "DELIVER", "Deliver the crew's sample", "Return to the habitat and right-click the sample analyzer, or hand the crew sample to Commander Vega.", preparation.completed(), 5, false);

        if (preparation.ready())
            return taskProgress(MissionPreparation.TaskKind.LAUNCH, "READY", "Crew checks complete!", operator
                    ? "Talk to Commander Vega again or press Begin mission. Hazards start only when you launch."
                    : "Your crew is ready. Ask Mission Control to begin the mission.", 5, 5, false);

        return taskProgress(MissionPreparation.TaskKind.BRIEFING, "TEAM", "Your task is complete", "Tell your crew what you finished. Help them find their marked stations while they complete the remaining checks.", preparation.completed(), 5, false);
    }

    private MissionProgressPayload taskProgress(final MissionPreparation.TaskKind kind, final String stage, final String title,
                                                final String instruction, final int completed, final int total, final boolean relay) {
        return progress(stage, title, instruction, kind == MissionPreparation.TaskKind.LAUNCH ? "Right-click Commander Vega" : "Right-click the marked object",
                taskPosition(kind), completed, total, preparation.taskStep(kind, relay));
    }

    private MissionProgressPayload progress(final String stage, final String title, final String instruction, final String action,
                                           final BlockPos target, final int completed, final int total, final int step) {
        return new MissionProgressPayload(stage, title, instruction, action, target.getX(), target.getY(), target.getZ(),
                completed, total, step, progressFeedback, preparation.ready());
    }

    private static boolean hasCrewKitClaim(final ServerPlayer player) {
        try { return GearClaims.claimed(player.level().getServer().getWorldPath(LevelResource.ROOT), player.getUUID(), GearClaims.Kit.MARS); }
        catch (final IOException error) {
            NimbusNovaMod.LOGGER.error("Could not read the crew kit claim", error);
            return true;
        }
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
            progressFeedback = mission.snapshot().lastEvent();
            broadcast(source.getServer());
            return 1;
        } catch (final IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private MissionState newPreparedMission() {
        final MissionState state = new MissionState(nextStorm());
        state.prepareEquipment(MissionState.Equipment.RESEARCH, preparation.checked(MissionPreparation.TaskKind.POWER));
        state.prepareEquipment(MissionState.Equipment.OXYGEN, preparation.checked(MissionPreparation.TaskKind.OXYGEN));
        return state;
    }

    private DustStorm nextStorm() {
        if (pinnedStorm != null) return pinnedStorm;

        final DustStorm[] storms = DustStorm.values();
        return storms[ThreadLocalRandom.current().nextInt(storms.length)];
    }

    private int pinStorm(final CommandSourceStack source, final DustStorm storm) {
        pinnedStorm = storm;
        if (mission.snapshot().phase() == MissionState.Phase.READY)
            mission = newPreparedMission();

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
        if (player.level().dimension().equals(ShipMap.DIMENSION)) {
            ShipController.get(player.level().getServer()).openConsole(player);
            return;
        }
        if (!ServerPlayNetworking.canSend(player, OpenConsolePayload.TYPE)) return;

        broadcast(player.level().getServer());
        ServerPlayNetworking.send(player, new OpenConsolePayload());
    }

    public int control(final CommandSourceStack source, final String action) {
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            source.sendFailure(Component.literal("Only Mission Control can change the mission phase."));
            return 0;
        }

        ReportStore.capture(source.getServer());
        try {
            switch (action) {
                case "start" -> {
                    if (cc.nimbusnova.mc.moon.MoonController.get(source.getServer()).active()
                            || ShipController.get(source.getServer()).selected()
                            || OutpostScenarioController.get(source.getServer()).selected())
                        throw new IllegalStateException("End the Moon mission first.");
                    if (source.getServer().getPlayerList().getPlayerCount() == 0)
                        throw new IllegalStateException("A crew member must join before the mission starts.");

                    if (!preparation.ready())
                        throw new IllegalStateException("Finish the crew checklist first: briefing, power repair, oxygen check, sample collection and delivery. Follow your mission marker.");
                    mission.start();
                    progressFeedback = "Mission launched! Keep your crew safe and complete the research objective.";
                }
                case "debug_start" -> {
                    mission.prepareEquipment(MissionState.Equipment.RESEARCH, true);
                    mission.prepareEquipment(MissionState.Equipment.OXYGEN, true);
                    mission.start();
                }
                case "pause" -> mission.pause();
                case "resume" -> {
                    LearningChecks.requireSolved(source.getServer(), "mars");
                    education.resume(mission);
                }
                case "acknowledge" -> {
                    LearningChecks.requireSolved(source.getServer(), "mars");
                    education.confirm();
                }
                case "abort" -> { mission.abort(); education.clear(); LearningChecks.clear(source.getServer(), "mars"); }
                case "reset" -> {
                    LearningChecks.clear(source.getServer(), "mars");
                    preparation = new MissionPreparation();
                    education.clear();
                    openTasks.clear();
                    mission = newPreparedMission();
                    progressFeedback = "New attempt ready. Meet Commander Vega before starting the clock.";
                }
                default -> throw new IllegalArgumentException("Unknown mission action: " + action);
            }

            ticks = 0;
            broadcast(source.getServer());
            ReportStore.capture(source.getServer());
            source.sendSuccess(() -> Component.literal("Mission: " + action), false);
            return 1;
        } catch (final IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private int assign(final CommandSourceStack source, final ServerPlayer player, final CrewRole role) {
        final MissionState.Phase phase = mission.snapshot().phase();
        if (!role(player.getUUID()).canChoose(phase, source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))) {
            source.sendFailure(Component.literal(phase.ended() ? "Choose a role when the next attempt is ready."
                    : "Your role is locked during flight. Mission Control can reassign it while paused."));
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
                    server.getPlayerList().getPlayerCount(),
                    education.incident() == null ? "" : education.incident().name(), education.confirmed()));
            if (ServerPlayNetworking.canSend(player, MissionProgressPayload.TYPE))
                ServerPlayNetworking.send(player, progress(player));
            if (justEnded && ServerPlayNetworking.canSend(player, OpenConsolePayload.TYPE))
                ServerPlayNetworking.send(player, new OpenConsolePayload());
        }
    }
}
