package cc.nimbusnova.mc.server;

import cc.nimbusnova.mc.hub.MissionHub;
import cc.nimbusnova.mc.learning.LearningChecks;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.vehicle.MarsRover;
import cc.nimbusnova.mc.vehicle.RoverContent;
import cc.nimbusnova.mc.scenario.OutpostScenarioController;
import cc.nimbusnova.mc.scenario.ScenarioActionPayload;
import cc.nimbusnova.mc.scenario.ScenarioContent;
import cc.nimbusnova.mc.world.scenario.ScenarioMaps;
import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.content.OutpostContent;
import cc.nimbusnova.mc.content.ShipContent;
import cc.nimbusnova.mc.report.ReportStore;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.ship.ShipActionPayload;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.ship.ShipTask;
import cc.nimbusnova.mc.ship.rooms.ShipRooms;
import cc.nimbusnova.mc.ship.rooms.RoomLighting;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.ship.ShipMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Set;
import java.util.Map;
import java.util.EnumMap;

public final class MissionServerGameTests {
    @GameTest(maxTicks = 400)
    public void worldsAndCooperativePhysicalRepairs(final GameTestHelper context) throws IOException {
        final var server = context.getLevel().getServer();
        ShipMap.create(server);
        final ServerLevel ship = server.getLevel(ShipMap.DIMENSION);
        if (ship == null) throw new AssertionError("The ship dimension was not loaded");

        for (final ShipMap.Marker marker : ShipMap.MARKERS)
            context.assertTrue(ship.getBlockState(marker.position()).is(ShipMap.block(marker.task())), "Imported station missing: " + marker);

        final var windows = ShipMap.windowPositions(server);
        context.assertFalse(windows.isEmpty(), "The ship has no observation windows");
        for (final BlockPos window : windows)
            context.assertTrue(ship.getBlockState(window).is(Blocks.GLASS), "An authored observation port was not fitted: " + window);

        context.assertTrue(ship.getBlockState(ShipMap.SPAWN.below()).isFaceSturdy(ship, ShipMap.SPAWN.below(), Direction.UP), "Bridge spawn has no supporting floor");
        final BlockPos playerChange = new BlockPos(0, 80, 22);
        final var original = ship.getBlockState(playerChange);
        ship.setBlock(playerChange, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        ShipMap.create(server);
        context.assertTrue(ship.getBlockState(playerChange).is(Blocks.GOLD_BLOCK), "Import overwrote a player change");
        ship.setBlock(playerChange, original, Block.UPDATE_CLIENTS);
        context.assertTrue(ship.getBlockState(ShipMap.MARKERS.getFirst().position()).is(ShipContent.COMMS_ARRAY), "Repeat import changed equipment");

        MarsTrainingMap.create(server.createCommandSourceStack());
        final ServerLevel mars = server.getLevel(MarsTrainingMap.DIMENSION);
        context.assertTrue(mars != null && mars.getBlockState(MarsTrainingMap.SAMPLE_ANALYZER).is(OutpostContent.SAMPLE_ANALYZER), "Mars equipment did not import");
        MarsTrainingMap.create(server.createCommandSourceStack());

        final ServerPlayer engineer = context.makeMockServerPlayerInLevel();
        final ServerPlayer lifeSupport = context.makeMockServerPlayerInLevel();
        server.getPlayerList().deop(new NameAndId(engineer.getGameProfile()));
        server.getPlayerList().deop(new NameAndId(lifeSupport.getGameProfile()));
        context.assertFalse(engineer.getUUID().equals(lifeSupport.getUUID()), "Cooperative actors must have different identities");
        context.assertFalse(engineer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || lifeSupport.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "Role checks require ordinary crew actors");
        if (mars == null) throw new AssertionError("The Mars dimension was not loaded");
        roverResources(context, engineer, mars);
        final ShipController controller = ShipController.get(server);
        controller.teacherAction(server, "ship_select");
        controller.action(engineer, new ShipActionPayload("role", BlockPos.ZERO, controller.mission().revision(), MoonMission.Role.POWER.ordinal()));
        controller.action(lifeSupport, new ShipActionPayload("role", BlockPos.ZERO, controller.mission().revision(), MoonMission.Role.LIFE_SUPPORT.ordinal()));
        context.assertTrue(controller.role(engineer) == MoonMission.Role.POWER && controller.role(lifeSupport) == MoonMission.Role.LIFE_SUPPORT,
                "Server actors could not claim different roles through the validated handler");

        final BlockPos storage = ShipMap.target(ShipTask.STORAGE, ShipMap.SPAWN);
        move(engineer, ship, storage.offset(0, 0, 1));
        ShipRooms.claimSupplies(engineer);
        move(lifeSupport, ship, storage.offset(0, 0, 1));
        ShipRooms.claimSupplies(lifeSupport);
        context.assertTrue(count(engineer, RepairContent.WIRE_KIT) == 2 && count(engineer, RepairContent.PATCH_PLATE.asItem()) == 4
                && count(lifeSupport, RepairContent.WIRE_KIT) == 0, "The shared repair pool was missing or duplicated across actors");

        RoomLighting.tick(server, false);
        context.assertTrue(RoomLighting.fixtureCount(server) > 0, "The ship lighting fixtures were not installed");
        final BlockPos engineLamp = BlockPos.betweenClosedStream(-12, 80, -41, 12, 87, -22)
                .filter(position -> ShipRooms.roomAt(position) == ShipRoomState.Room.ENGINEERING)
                .filter(position -> ship.getBlockState(position).is(RoomLighting.LAMP) || ship.getBlockState(position).is(Blocks.LIGHT))
                .findFirst().orElseThrow().immutable();
        context.assertTrue(ship.getBlockState(engineLamp).getLightEmission() == 12, "Powered engineering did not illuminate its fixtures");
        final BlockPos lightSwitch = RoomLighting.switchPosition(server, ShipRoomState.Room.ENGINEERING);
        context.assertTrue(ship.getBlockState(lightSwitch).is(RoomLighting.SWITCH), "Engineering has no physical light switch");
        move(engineer, ship, lightSwitch);
        use(engineer, lightSwitch, Direction.NORTH);
        context.assertTrue(ship.getBlockState(engineLamp).getLightEmission() <= 3, "The physical switch did not dim engineering lighting");
        use(engineer, lightSwitch, Direction.NORTH);
        context.assertTrue(ship.getBlockState(engineLamp).getLightEmission() == 12, "The physical switch did not restore engineering lighting");

        ShipRooms.trigger(server, "ENGINEERING", "POWER");
        RoomLighting.tick(server, false);
        context.assertTrue(ship.getBlockState(engineLamp).getLightEmission() <= 3, "A power failure left full room lighting on");
        final BlockPos wire = ShipRooms.wirePosition();
        move(engineer, ship, wire.offset(-2, -1, 0));
        final int revision = controller.mission().revision();
        controller.action(engineer, new ShipActionPayload("task", wire, revision, 0));
        context.assertTrue(controller.mission().revision() == revision && !ShipRooms.state(server).powered(ShipRoomState.Room.ENGINEERING),
                "Unpowered equipment still operated");

        transfer(engineer, lifeSupport, RepairContent.WIRE_KIT, 1);
        equip(lifeSupport, RepairContent.WIRE_KIT);
        move(lifeSupport, ship, wire.offset(-2, -1, 0));
        use(lifeSupport, wire, Direction.WEST);
        context.assertTrue(!ShipRooms.state(server).powered(ShipRoomState.Room.ENGINEERING)
                && count(lifeSupport, RepairContent.WIRE_KIT) == 1, "A life-support actor bypassed engineering repair permissions");
        equip(engineer, RepairContent.WIRE_KIT);
        use(engineer, wire, Direction.WEST);
        context.assertTrue(ShipRooms.state(server).powered(ShipRoomState.Room.ENGINEERING)
                && count(engineer, RepairContent.WIRE_KIT) == 0, "Engineering repair did not restore power and consume the kit");

        RoomLighting.tick(server, false);
        context.assertTrue(ship.getBlockState(engineLamp).getLightEmission() == 12, "Cable repair did not restore actual light emission");

        final BlockPos breach = ShipRooms.breachPosition();
        final BlockState hull = ship.getBlockState(breach), outerHull = ship.getBlockState(breach.east());
        ShipRooms.trigger(server, "SCIENCE", "BREACH");
        for (int second = 0; second < 3; second++)
            ShipRooms.tick(server, false);
        context.assertTrue(ship.getBlockState(breach).isAir() && ship.getBlockState(breach.east()).isAir() && ShipRooms.state(server).pressure(ShipRoomState.Room.SCIENCE) == 70,
                "The breach did not create a real hole and pressure loss");
        context.assertTrue(Boolean.FALSE.equals(ShipRooms.doorOverride(ship, new BlockPos(3, 76, -18))), "The breached room did not isolate its door");
        context.assertFalse(Boolean.FALSE.equals(ShipRooms.doorOverride(ship, new BlockPos(3, 81, -19))), "A lower-deck breach isolated an unrelated upper-deck door");

        transfer(engineer, lifeSupport, RepairContent.PATCH_PLATE.asItem(), 4);
        equip(lifeSupport, RepairContent.PATCH_PLATE.asItem());
        move(lifeSupport, ship, breach.offset(-1, -1, 0));
        use(lifeSupport, breach.below(), Direction.UP);
        context.assertTrue(ship.getBlockState(breach).is(RepairContent.PATCH_PLATE)
                && count(lifeSupport, RepairContent.PATCH_PLATE.asItem()) == 3, "Life support did not place and consume a real repair plate");
        ShipRooms.tick(server, false);
        ShipRooms.tick(server, false);
        context.assertTrue(ShipRooms.state(server).pressure(ShipRoomState.Room.SCIENCE) == 80 && !ShipRooms.hasUnresolved(server),
                "A patched room did not recover safe pressure");
        ShipRooms.recoverOwnedBreach(server);
        context.assertTrue(ship.getBlockState(breach).equals(hull) && ship.getBlockState(breach.east()).equals(outerHull), "Owned-cell recovery did not restore the original hull");
        move(engineer, ship, wire.offset(-2, -1, 0));
        for (int socket = 0; socket < 4; socket++) {
            final int cartridge = controller.mission().aux(ShipTask.ENGINE) >> socket * 4 & 15;
            controller.action(engineer, new ShipActionPayload("task", wire, controller.mission().revision(), socket * 4 + cartridge - 1));
        }
        context.assertTrue(controller.mission().completed(ShipTask.ENGINE), "Engine setup rejected at " + engineer.position() + "; op=" + engineer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) + "; board=" + controller.mission().board(ShipTask.ENGINE));
        controller.action(engineer, new ShipActionPayload("role", BlockPos.ZERO, controller.mission().revision(), MoonMission.Role.SUPPLIES.ordinal()));
        context.assertTrue(controller.role(engineer) == MoonMission.Role.SUPPLIES, "READY role reassignment to supplies was rejected");
        move(engineer, ship, storage.west());
        controller.action(engineer, new ShipActionPayload("task", storage, controller.mission().revision(), 0));
        controller.action(engineer, new ShipActionPayload("task", storage, controller.mission().revision(), 2));
        context.assertTrue(controller.mission().board(ShipTask.STORAGE) == 17, "Cargo placement rejected at " + engineer.position() + "; board=" + controller.mission().board(ShipTask.STORAGE));
        controller.action(engineer, new ShipActionPayload("hold_begin", storage, controller.mission().revision(), 3));
        context.runAfterDelay(25, () -> {
            context.assertTrue(engineer.level() == ship && engineer.distanceToSqr(Vec3.atCenterOf(storage)) < 36, "Cargo actor moved out of reach while holding: " + engineer.position());
            controller.action(engineer, new ShipActionPayload("hold_finish", storage, controller.mission().revision(), 3));
            context.assertTrue(controller.mission().ready(), "Physical power and cargo setup did not enable launch");
            controller.action(engineer, new ShipActionPayload("role", BlockPos.ZERO, controller.mission().revision(), MoonMission.Role.POWER.ordinal()));
            controller.teacherAction(server, "ship_start");
            final var launched = controller.mission().snapshot();
            controller.teacherAction(server, "ship_start");
            context.assertTrue(controller.mission().snapshot().equals(launched), "A repeated launch changed an already launched attempt");
            boolean skippedLearning = false;
            try {
                controller.teacherAction(server, "ship_resume");
            } catch (final IllegalStateException expected) {
                skippedLearning = true;
            }
            context.assertTrue(skippedLearning, "Teacher resume bypassed the crew reading check");
            final var check = LearningChecks.snapshot(server, "ship", false);
            LearningChecks.applyAnswer(engineer, "ship", check.revision(), 0);
            context.assertFalse(LearningChecks.snapshot(server, "ship", false).solved(), "A wrong docking prediction passed the reading check");
            LearningChecks.applyAnswer(engineer, "ship", check.revision(), 1);
            controller.teacherAction(server, "ship_acknowledge");
            controller.teacherAction(server, "ship_resume");
            final var active = controller.mission().snapshot();
            controller.teacherAction(server, "ship_start");
            context.assertTrue(controller.mission().snapshot().equals(active), "Repeated active launch reset the mission");
            controller.teacherAction(server, "ship_end");
            ReportStore.capture(server);
            try {
                final var recent = ReportStore.list(server).getAsJsonArray("reports");
                final var row = recent.asList().stream().map(element -> element.getAsJsonObject())
                        .filter(item -> item.get("mission").getAsString().equals("ship")).findFirst().orElseThrow();
                final var report = ReportStore.read(server, row.get("id").getAsString());
                context.assertTrue(report != null && report.has("timeline") && !report.toString().contains(engineer.getUUID().toString())
                        && !report.toString().contains(engineer.getName().getString()), "Saved debrief was missing or contained a crew identity");
            } catch (final IOException error) {
                throw new AssertionError("Could not reopen the saved ship debrief", error);
            }

            final Map<MoonMission.Role, ServerPlayer> crew = new EnumMap<>(MoonMission.Role.class);
            crew.put(MoonMission.Role.POWER, engineer);
            crew.put(MoonMission.Role.LIFE_SUPPORT, lifeSupport);
            for (final MoonMission.Role role : new MoonMission.Role[] { MoonMission.Role.SUPPLIES, MoonMission.Role.MAINTENANCE, MoonMission.Role.PILOT }) {
                final ServerPlayer actor = context.makeMockServerPlayerInLevel();
                server.getPlayerList().deop(new NameAndId(actor.getGameProfile()));
                crew.put(role, actor);
            }
            context.assertTrue(crew.values().stream().map(ServerPlayer::getUUID).distinct().count() == 5, "Five role actors need distinct identities");
            final MissionHub hub = MissionHub.get(server);
            for (final String id : new String[] { "mars", "moon", "ship", "shelter", "long_night", "greenhouse" }) {
                hub.action(server, "select", id);
                context.assertTrue(hub.selected(server).equals(id), "Unified hub did not select " + id);
                if (id.equals("shelter") || id.equals("long_night") || id.equals("greenhouse"))
                    scenarioRoles(context, crew, id);
                final var state = hub.state(server).deepCopy();
                hub.action(server, "select", id);
                context.assertTrue(hub.state(server).equals(state), "Repeated selection reset " + id);
            }
            boolean invalidRejected = false;
            try {
                hub.action(server, "select", "missing_mission");
            } catch (final IllegalArgumentException expected) {
                invalidRejected = true;
            }
            context.assertTrue(invalidRejected && hub.selected(server).equals("greenhouse"), "Invalid mission selection changed the running mission");
            hub.action(server, "end", "");
            for (final ServerPlayer actor : crew.values()) server.getPlayerList().remove(actor);
            hub.action(server, "select", "mars");
            context.assertFalse(hub.state(server).get("ready").getAsBoolean(), "An empty classroom was marked ready");
            hub.action(server, "end", "");
            System.out.println("Headless world checks passed: six map selections, five role-bound server actors, physical repairs, windows/lights, timed cargo and idempotent launch.");
            context.succeed();
        });
    }

    private static void roverResources(final GameTestHelper context, final ServerPlayer driver, final ServerLevel level) {
        final var server = level.getServer();
        final MarsRover rover = RoverContent.ROVER.create(level, EntitySpawnReason.COMMAND);
        if (rover == null) throw new AssertionError("The registered rover could not be created");
        rover.snapTo(29.5, 66, 20.5, 0, 0);
        if (!level.addFreshEntity(rover)) throw new AssertionError("The real rover could not be placed in the workshop");
        move(driver, level, rover.blockPosition().north());
        server.getCommands().performPrefixedCommand(driver.createCommandSourceStack(), "mission role power");
        driver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.MARS_SAMPLE, 4));
        for (int sample = 0; sample < 4; sample++) rover.interact(driver, InteractionHand.MAIN_HAND, Vec3.ZERO);
        context.assertTrue(rover.cargoCount() == 3 && count(driver, ModContent.MARS_SAMPLE) == 1, "The real rover cargo bay did not enforce three samples");
        context.assertTrue(driver.startRiding(rover), "Could not board the rover in the workshop");
        driver.setLastClientInput(new Input(true, false, false, false, false, false, false));
        final float initialCharge = rover.battery();
        for (int tick = 0; tick < 6; tick++) rover.tick();
        context.assertTrue(rover.battery() < initialCharge && rover.distanceToSqr(28.5, 66, 25.5) < 64, "Driven distance did not spend rover power inside the workshop range");
        driver.setLastClientInput(Input.EMPTY);
        driver.stopRiding();
        move(driver, level, rover.blockPosition().north());
        driver.setShiftKeyDown(true);
        rover.interact(driver, InteractionHand.MAIN_HAND, Vec3.ZERO);
        context.assertTrue(rover.cargoCount() == 0 && count(driver, ModContent.MARS_SAMPLE) == 4, "Unloading the real rover lost or duplicated cargo");
        driver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.ENGINEERING_TOOL));
        final MissionController mars = MissionController.get(server);
        mars.control(server.createCommandSourceStack(), "debug_start");
        final double baseCharge = mars.snapshot().battery();
        rover.interact(driver, InteractionHand.MAIN_HAND, Vec3.ZERO);
        context.assertTrue(rover.battery() == 100 && mars.snapshot().battery() == baseCharge - 5, "Workshop charging did not use the shared battery");
        mars.control(server.createCommandSourceStack(), "reset");
        driver.setShiftKeyDown(false);
        rover.discard();
    }

    private static void scenarioRoles(final GameTestHelper context, final Map<MoonMission.Role, ServerPlayer> crew, final String id) {
        final var server = context.getLevel().getServer();
        final OutpostScenarioController controller = OutpostScenarioController.get(server);
        final ServerLevel level = server.getLevel(ScenarioMaps.dimension(id));
        if (level == null) throw new AssertionError("Scenario world missing: " + id);
        for (final var entry : crew.entrySet()) {
            controller.playerAction(entry.getValue(), new ScenarioActionPayload("role", 0, entry.getKey().ordinal(), controller.mission().revision));
            final var snapshot = controller.playerState(entry.getValue());
            final BlockPos target = controller.roleTarget(entry.getValue());
            context.assertTrue(snapshot.get("role").getAsString().equals(entry.getKey().name())
                    && !snapshot.get("roleObjective").getAsString().isBlank()
                    && snapshot.get("targetX").getAsInt() == target.getX() && snapshot.get("targetZ").getAsInt() == target.getZ(),
                    "The assigned role has no matching objective route: " + id + "/" + entry.getKey());
            context.assertFalse(entry.getValue().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "Scenario role coverage used an operator bypass");
        }
        final ServerPlayer power = crew.get(MoonMission.Role.POWER), life = crew.get(MoonMission.Role.LIFE_SUPPORT),
                supplies = crew.get(MoonMission.Role.SUPPLIES), maintenance = crew.get(MoonMission.Role.MAINTENANCE), pilot = crew.get(MoonMission.Role.PILOT);
        move(life, level, ScenarioMaps.station(0).south());
        final int deniedRevision = controller.mission().revision;
        controller.playerAction(life, new ScenarioActionPayload("task", 0, 0, deniedRevision));
        context.assertTrue(controller.mission().revision == deniedRevision, "The wrong role operated a power station in " + id);
        move(supplies, level, ScenarioMaps.station(3).south());
        controller.playerAction(supplies, new ScenarioActionPayload("supplies", 3, 0, controller.mission().revision));
        final Item stores = id.equals("shelter") ? ScenarioContent.SHIELD.asItem() : id.equals("long_night") ? RepairContent.WIRE_KIT : RepairContent.PATCH_PLATE.asItem();
        context.assertTrue(count(supplies, stores) == (id.equals("shelter") ? 3 : 2), "Supplies role did not receive the finite stores in " + id);

        if (id.equals("shelter")) {
            context.assertTrue(controller.roleTarget(maintenance).equals(ScenarioMaps.shieldSlots().getFirst()), "Shield builder was sent to the wrong task");
            for (int bearing = 0; bearing < 3; bearing++) scenarioTask(context, controller, power, level, 0, bearing);
            scenarioTask(context, controller, life, level, 1, 1);
            scenarioTask(context, controller, life, level, 1, 1);
            transfer(supplies, maintenance, stores, 3);
            equip(maintenance, stores);
            for (final BlockPos slot : ScenarioMaps.shieldSlots()) {
                move(maintenance, level, slot.south());
                use(maintenance, slot.below(), Direction.UP);
                context.assertTrue(level.getBlockState(slot).is(ScenarioContent.SHIELD), "Maintenance could not build the actual shielding");
            }
            context.assertTrue(controller.mission().shields == 7, "Built shielding did not reach the shared mission state");
            controller.action(server, "start");
            scenarioTask(context, controller, pilot, level, 2, 0);
            context.assertTrue(controller.mission().signal == 1, "Pilot could not begin the SOS beacon");
        } else if (id.equals("long_night")) {
            context.assertTrue(controller.roleTarget(maintenance).equals(ScenarioMaps.station(1)), "Night recorder objective sent maintenance to the wrong station");
            scenarioTask(context, controller, power, level, 0, 2);
            scenarioTask(context, controller, life, level, 1, 2);
            scenarioTask(context, controller, life, level, 1, 2);
            scenarioTask(context, controller, maintenance, level, 1, 0);
            context.assertTrue(controller.mission().labArmed, "Maintenance could not arm the recorder");
            scenarioTask(context, controller, power, level, 0, 2);
            controller.action(server, "start");
            scenarioTask(context, controller, pilot, level, 2, 0);
            context.assertTrue(controller.mission().signal == 1, "Night pilot could not use the beacon");
            // Inject the scripted fault at the repair boundary without waiting through the mission timer.
            controller.mission().cableBroken = true;
            transfer(supplies, maintenance, stores, 2);
            equip(maintenance, stores);
            final BlockPos bus = ScenarioMaps.station(0);
            move(maintenance, level, bus.south());
            controller.action(server, "pause");
            context.assertTrue(use(maintenance, bus, Direction.SOUTH) == InteractionResult.FAIL
                    && controller.mission().cableBroken && count(maintenance, stores) == 2, "A paused wire repair consumed stores or cleared the fault");
            controller.action(server, "abort");
            context.assertTrue(use(maintenance, bus, Direction.SOUTH) == InteractionResult.FAIL
                    && controller.mission().cableBroken && count(maintenance, stores) == 2, "An ended wire repair consumed stores or cleared the fault");
        } else {
            context.assertTrue(controller.roleTarget(supplies).equals(ScenarioMaps.station(2)), "Issued greenhouse stores did not route supplies to crop sampling");
            for (int bed = 0; bed < 3; bed++) scenarioTask(context, controller, power, level, 0, bed);
            scenarioTask(context, controller, maintenance, level, 0, 3);
            scenarioTask(context, controller, life, level, 1, 0);
            scenarioTask(context, controller, pilot, level, 2, 3);
            context.assertTrue(controller.mission().pumpInspected && controller.mission().extractionVent, "Maintenance/pilot preparation did not affect greenhouse equipment");
            controller.action(server, "start");
            // Stage a ready specimen: this check covers role routing, not the timed care loop.
            controller.mission().elapsed = 80;
            controller.mission().moisture[0] = 55;
            controller.mission().rootAir[0] = 80;
            controller.mission().lightSeconds[0] = 10;
            controller.mission().cabinHumidity = 55;
            move(pilot, level, ScenarioMaps.station(2).south());
            final int revision = controller.mission().revision;
            controller.playerAction(pilot, new ScenarioActionPayload("task", 2, 0, revision));
            context.assertTrue(controller.mission().revision == revision, "Pilot bypassed the supplies harvest role");
            scenarioTask(context, controller, supplies, level, 2, 0);
            context.assertTrue((controller.mission().harvest & 1) != 0, "Supplies could not archive the prepared crop observation");
            final BlockPos pipe = OutpostScenarioController.PIPE;
            // Exercise the actual placement callback against a pre-existing pipe fault.
            level.setBlock(pipe, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            controller.mission().pipeBroken = true;
            transfer(supplies, maintenance, stores, 2);
            equip(maintenance, stores);
            move(maintenance, level, pipe.south());
            controller.action(server, "pause");
            context.assertTrue(use(maintenance, pipe.below(), Direction.UP) == InteractionResult.FAIL
                    && level.getBlockState(pipe).isAir() && count(maintenance, stores) == 2 && controller.mission().pipeBroken,
                    "A paused pipe repair placed a plate or consumed stores");
            controller.action(server, "resume");
            final BlockPos wrongTarget = pipe.south();
            final BlockState targetBefore = level.getBlockState(wrongTarget);
            context.assertTrue(use(maintenance, wrongTarget.below(), Direction.UP) == InteractionResult.FAIL
                    && level.getBlockState(wrongTarget).equals(targetBefore) && count(maintenance, stores) == 2 && controller.mission().pipeBroken,
                    "An off-target plate fell through to normal block placement");
        }
        System.out.println("Five-role scenario routing passed: " + id);
    }

    private static void scenarioTask(final GameTestHelper context, final OutpostScenarioController controller, final ServerPlayer actor,
                                     final ServerLevel level, final int station, final int value) {
        move(actor, level, ScenarioMaps.station(station).south());
        final int revision = controller.mission().revision;
        controller.playerAction(actor, new ScenarioActionPayload("task", station, value, revision));
        context.assertTrue(controller.mission().revision > revision, "Assigned role could not use station " + station + "/" + value + " in " + controller.mission().id);
    }

    private static void move(final ServerPlayer player, final ServerLevel level, final BlockPos position) {
        player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, Set.of(), 0, 0, true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static InteractionResult use(final ServerPlayer player, final BlockPos position, final Direction face) {
        return UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position).add(face.getUnitVec3().scale(0.5)), face, position, false));
    }

    private static int count(final ServerPlayer player, final Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void transfer(final ServerPlayer from, final ServerPlayer to, final Item item, final int amount) {
        for (int slot = 0; slot < from.getInventory().getContainerSize(); slot++) {
            if (!from.getInventory().getItem(slot).is(item)) continue;
            final ItemStack transferred = from.getInventory().removeItem(slot, amount);
            if (transferred.getCount() != amount || !to.addItem(transferred)) throw new AssertionError("Repair supply transfer failed");
            return;
        }
        throw new AssertionError("No repair supplies to transfer");
    }

    private static void equip(final ServerPlayer player, final Item item) {
        if (player.getMainHandItem().is(item)) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (!player.getInventory().getItem(slot).is(item)) continue;
            final ItemStack previous = player.getMainHandItem().copy();
            final ItemStack stack = player.getInventory().removeItem(slot, 64);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            if (!previous.isEmpty()) player.addItem(previous);
            return;
        }
        throw new AssertionError("No repair item to equip");
    }
}
