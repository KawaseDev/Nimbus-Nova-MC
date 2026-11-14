package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.moon.MoonController;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.MarsTerrain;
import cc.nimbusnova.mc.world.MarsGravity;
import cc.nimbusnova.mc.npc.MissionAstronaut;
import cc.nimbusnova.mc.npc.MissionNpcs;
import cc.nimbusnova.mc.vehicle.MarsRover;
import cc.nimbusnova.mc.client.npc.NpcDialogueScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class MissionClientSmokeTest implements FabricClientGameTest {
    @Override
    public void runTest(final ClientGameTestContext context) {
        context.setScreen(TitleScreen::new);
        boolean titleCorrect = context.computeOnClient(client -> {
            var children = client.gui.screen().children();
            return children.stream().noneMatch(child -> child instanceof Button b && b.getMessage().equals(Component.translatable("menu.multiplayer")))
                    && children.stream().anyMatch(child -> child instanceof Button b && b.getMessage().getString().equals("Join session"))
                    && children.stream().anyMatch(child -> child instanceof Button b && b.getMessage().equals(Component.translatable("menu.online")));
        });
        if (!titleCorrect) throw new AssertionError("The title screen must replace Multiplayer with Join session and retain Realms");
        context.clickScreenButton("Join session");
        context.waitForScreen(JoinSessionScreen.class);
        context.setScreen(TitleScreen::new);
        ServerData sessionServer = new ServerData("Nimbus Nova", "127.0.0.1:25565", ServerData.Type.OTHER);
        ServerData otherServer = new ServerData("Other", "127.0.0.1:25565", ServerData.Type.OTHER);
        SessionIdentity.prepare("MoonTest42", true, sessionServer);
        if (SessionIdentity.takeLogin(otherServer) != null || !"MoonTest42".equals(SessionIdentity.takeLogin(sessionServer)))
            throw new AssertionError("The chosen offline name must apply only to its session connection");
        SessionIdentity.prepare("A name with spaces", true, sessionServer);
        String mapped = SessionIdentity.takeLogin(sessionServer);
        if (mapped == null || !mapped.matches("[A-Za-z0-9_]{1,16}") || !mapped.equals(SessionIdentity.minecraftName("A name with spaces")))
            throw new AssertionError("Unrestricted roster names need a stable valid Minecraft profile name");
        SessionIdentity.prepare("MoonTest42", false, sessionServer);
        if (SessionIdentity.takeLogin(sessionServer) != null)
            throw new AssertionError("Online servers must retain the authenticated profile");
        try (final TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> NimbusNovaClient.snapshot != null && GuidedMissionClient.progress() != null);
            if (!ObjectiveGuidance.turnLabel(-90).equals("To your left") || !ObjectiveGuidance.turnLabel(90).equals("To your right"))
                throw new AssertionError("Objective directions must agree with Minecraft yaw");
            final MarsMovementChecks.Sample earthMovement = world.getServer().computeOnServer(
                    server -> MarsMovementChecks.sample(world.getConnection().getServerLevel()));
            world.getServer().runOnServer(server -> server.getPlayerList().op(new NameAndId(world.getConnection().getServerPlayer().getGameProfile())));
            world.getServer().runOnServer(server -> MoonController.get(server).teacherAction(server, "moon_rescue"));
            context.waitForScreen(MoonScreen.class);
            context.clickScreenButton("SUPPLIES");
            context.waitFor(client -> NimbusNovaClient.moon != null && NimbusNovaClient.moon.role().equals("SUPPLIES"));
            if (context.computeOnClient(client -> NimbusNovaClient.moon.water() != 100 || NimbusNovaClient.moon.battery() != -1))
                throw new AssertionError("Only role-specific Moon telemetry should be sent to a student");
            context.clickScreenButton("Carry spare parts");
            context.waitFor(client -> NimbusNovaClient.moon.decisionMade());
            context.clickScreenButton("Confirm report");
            context.waitFor(client -> (NimbusNovaClient.moon.confirmed() & 1 << MoonMission.Role.SUPPLIES.ordinal()) != 0);
            world.getServer().runOnServer(server -> {
                MoonController controller = MoonController.get(server);
                for (MoonMission.Role role : MoonMission.Role.values()) if (role != MoonMission.Role.SUPPLIES) controller.mission().confirm(role);
                controller.teacherAction(server, "moon_advance");
            });
            context.waitFor(client -> NimbusNovaClient.moon != null && NimbusNovaClient.moon.incident().equals("DOCKING"));
            GuidedMissionChecks.capture(context, "moon-docking-education");
            context.clickScreenButton("Next");
            GuidedMissionChecks.capture(context, "moon-docking-evidence");
            world.getServer().runOnServer(server -> {
                MoonController controller = MoonController.get(server);
                boolean rejected = false;
                try { controller.teacherAction(server, "moon_resume"); } catch (IllegalStateException expected) { rejected = true; }
                if (!rejected) throw new AssertionError("Moon lesson resumed without teacher confirmation");
                controller.teacherAction(server, "moon_acknowledge");
                controller.teacherAction(server, "moon_resume");
                controller.teacherAction(server, "moon_end");
            });
            context.waitFor(client -> NimbusNovaClient.moon == null);
            context.setScreen(() -> null);
            world.getServer().runCommand("mars create");
            world.getServer().runCommand("mars create");
            world.getServer().runCommand("mars");
            world.getServer().runCommand("execute as @p run mars join");
            context.waitFor(client -> client.level != null && client.level.dimension().equals(MarsTrainingMap.DIMENSION));
            final MarsMovementChecks.Sample marsMovement = world.getServer().computeOnServer(
                    server -> MarsMovementChecks.sample(world.getConnection().getServerLevel()));
            if (Math.abs(marsMovement.gravity() - earthMovement.gravity() * MarsGravity.EARTH_GRAVITY_RATIO) > 0.000001
                    || Math.abs(marsMovement.itemGravity() - earthMovement.itemGravity() * MarsGravity.EARTH_GRAVITY_RATIO) > 0.000001)
                throw new AssertionError("Mars gravity must scale living entities and dropped items consistently");

            if (marsMovement.jumpHeight() < earthMovement.jumpHeight() * 1.8
                    || marsMovement.airborneTicks() < earthMovement.airborneTicks() * 1.5)
                throw new AssertionError("Mars movement must produce higher jumps and longer airtime");

            if (marsMovement.fallDamage() >= earthMovement.fallDamage())
                throw new AssertionError("The same fall must cause less damage under Mars gravity");

            final double clientGravity = context.computeOnClient(client -> world.getConnection().getClientPlayer().getGravity());
            if (Math.abs(clientGravity - marsMovement.gravity()) > 0.000001)
                throw new AssertionError("Client prediction and server gravity disagree");

            System.out.println("Movement checks: Earth=" + earthMovement + "; Mars=" + marsMovement);
            world.getConnection().waitForChunksRender();
            GuidedMissionChecks.capture(context, "mars-outpost");
            context.runOnClient(client -> client.gui.hud.toggle());
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 50 88 -53 42 8");
            context.waitFor(client -> client.player != null && client.player.getY() > 80);
            context.waitTicks(5);
            world.getConnection().waitForChunksRender();
            GuidedMissionChecks.capture(context, "dome-exterior");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 12 68 -9 35 -13");
            context.waitFor(client -> client.player != null && client.player.getX() < 15);
            context.waitTicks(5);
            world.getConnection().waitForChunksRender();
            GuidedMissionChecks.capture(context, "dome-interior");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -7 66 -7 -35 -8");
            context.waitTicks(10);
            GuidedMissionChecks.capture(context, "geometry-habitat-wall");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -8 66 0 -45 17");
            context.waitTicks(10);
            GuidedMissionChecks.capture(context, "geometry-life-support");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 4 66 -6 -40 17");
            context.waitTicks(10);
            GuidedMissionChecks.capture(context, "geometry-locker-closed");
            world.getServer().runOnServer(server -> {
                final var level = world.getConnection().getServerLevel();
                level.setBlock(MarsTrainingMap.SUPPLY_LOCKER, level.getBlockState(MarsTrainingMap.SUPPLY_LOCKER)
                        .setValue(BlockStateProperties.OPEN, true), 2);
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            GuidedMissionChecks.capture(context, "geometry-locker-open");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -6 66 0 40 14");
            context.waitTicks(10);
            GuidedMissionChecks.capture(context, "geometry-air-tanks");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 5 66 2 -35 -5");
            context.waitTicks(10);
            GuidedMissionChecks.capture(context, "geometry-cryo-cabinet");

            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 12 66 -7 -15 10");
            context.waitTicks(10);
            GuidedMissionChecks.capture(context, "geometry-weather-greenhouse");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 0 66 15 0 -5");
            context.waitTicks(10);
            GuidedMissionChecks.capture(context, "geometry-shelter");
            context.runOnClient(client -> client.gui.hud.toggle());
            AtmosphereChecks.entrance(context, world);
            GuidedMissionChecks.checkOutpost(context, world);
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 11 66 -3 90 -6");
            context.waitTicks(10);
            context.runOnClient(client -> client.gui.hud.toggle());
            GuidedMissionChecks.capture(context, "dark-habitat-materials");
            context.runOnClient(client -> client.gui.hud.toggle());
            if (MissionTutorial.consoleOpened)
                throw new AssertionError("The tutorial must start with the console step unchecked");

            GuidedMissionChecks.capture(context, "tutorial-start");
            context.setScreen(MissionConsoleScreen::new);
            context.clickScreenButton("Crew");
            context.clickScreenButton("Power engineer");
            context.waitFor(client -> NimbusNovaClient.snapshot.role() == CrewRole.POWER);
            context.setScreen(() -> null);
            GuidedMissionChecks.capture(context, "tutorial-ready");
            if (!MissionTutorial.consoleOpened)
                throw new AssertionError("Opening the console must tick the tutorial's first step");

            GuidedMissionChecks.prepare(context, world);
            AtmosphereChecks.suit(context, world);
            ScienceStationChecks.practice(context, world);
            context.setScreen(MissionConsoleScreen::new);
            context.clickScreenButton("Start mission");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.ACTIVE);
            world.getServer().runOnServer(server -> {
                final MissionController controller = MissionController.get(server);
                for (int tick = 0; tick < 400 && controller.education().incident() == null; tick++)
                    ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
                if (controller.education().incident() == null || controller.snapshot().phase() != MissionState.Phase.PAUSED)
                    throw new AssertionError("The solar warning must open an educational pause");
            });
            context.waitFor(client -> NimbusNovaClient.snapshot != null && !NimbusNovaClient.snapshot.educationIncident().isEmpty());
            context.waitForScreen(MissionConsoleScreen.class);
            GuidedMissionChecks.capture(context, "solar-education-pause");
            context.clickScreenButton("Next lesson");
            GuidedMissionChecks.capture(context, "solar-education-hazards");
            context.clickScreenButton("Next lesson");
            GuidedMissionChecks.capture(context, "solar-education-source");
            world.getServer().runCommand("mission acknowledge");
            world.getServer().runCommand("mission resume");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.ACTIVE);
            context.setScreen(() -> null);
            ScienceStationChecks.active(context, world);
            context.setScreen(MissionConsoleScreen::new);
            GuidedMissionChecks.capture(context, "mission-overview");
            context.clickScreenButton("Crew");
            context.clickScreenButton("Research: ON");
            context.waitFor(client -> !NimbusNovaClient.snapshot.state().researchOnline());
            world.getServer().runOnServer(server -> {
                if (world.getConnection().getServerLevel().getBlockState(MarsTrainingMap.RESEARCH_STATION).getValue(BlockStateProperties.POWERED))
                    throw new AssertionError("Research console animation must follow the server equipment state");
            });
            GuidedMissionChecks.capture(context, "mission-systems");
            context.clickScreenButton("Science");
            GuidedMissionChecks.capture(context, "mission-briefing");
            context.setScreen(() -> null);
            GuidedMissionChecks.capture(context, "mission-hud");
            world.getServer().runCommand("mission equipment research true");
            world.getServer().runOnServer(server -> {
                final var player = world.getConnection().getServerPlayer();
                // Each mission allows MissionState.OXYGEN_CANISTERS refills, whichever item or station supplies them.
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.OXYGEN_CANISTER, 3));
                ModContent.OXYGEN_CANISTER.use(player.level(), player, InteractionHand.MAIN_HAND);
                if (player.getMainHandItem().getCount() != 2)
                    throw new AssertionError("A successful oxygen refill must consume exactly one canister");

                ModContent.OXYGEN_CANISTER.use(player.level(), player, InteractionHand.MAIN_HAND);
                ModContent.OXYGEN_CANISTER.use(player.level(), player, InteractionHand.MAIN_HAND);
                if (player.getMainHandItem().getCount() != 1)
                    throw new AssertionError("Refills beyond the mission's canister supply must not consume another item");

                player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.SPACE_HELMET));
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.SPACE_CHESTPLATE));
                player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.SPACE_LEGGINGS));
                player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModContent.SPACE_BOOTS));
                player.getInventory().add(new ItemStack(ModContent.MISSION_TABLET));
                player.getInventory().add(new ItemStack(ModContent.ENGINEERING_TOOL));
                player.getInventory().add(new ItemStack(ModContent.MARS_SAMPLE));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.MISSION_TABLET));
                ModContent.MISSION_TABLET.use(player.level(), player, InteractionHand.MAIN_HAND);
            });
            context.waitFor(client -> client.gui.screen() instanceof MissionConsoleScreen);
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> client.setScreenAndShow(new InventoryScreen(world.getConnection().getClientPlayer())));
            GuidedMissionChecks.capture(context, "expedition-equipment");
            context.setScreen(() -> null);
            world.getServer().runOnServer(server -> {
                final var player = world.getConnection().getServerPlayer();
                server.getPlayerList().deop(new NameAndId(player.getGameProfile()));
                UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(MarsTrainingMap.RESEARCH_STATION), Direction.UP,
                                MarsTrainingMap.RESEARCH_STATION, false));
                UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(MarsTrainingMap.OXYGEN_STATION), Direction.UP,
                                MarsTrainingMap.OXYGEN_STATION, false));
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "mission reset");
            });
            world.getConnection().waitForClientboundPackets();
            if (context.computeOnClient(client -> NimbusNovaClient.snapshot.state().researchOnline()))
                throw new AssertionError("The power engineer could not disable research at its station");

            if (!context.computeOnClient(client -> NimbusNovaClient.snapshot.state().oxygenOnline()))
                throw new AssertionError("The power engineer was allowed to operate the oxygen station");

            if (context.computeOnClient(client -> NimbusNovaClient.snapshot.state().phase()) != MissionState.Phase.ACTIVE)
                throw new AssertionError("A non-operator reset the mission");

            context.runOnClient(client -> client.gui.hud.toggle());
            world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            world.getServer().runCommand("mission equipment research true");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -3.5 66 -6.5 0 0");
            world.getConnection().waitForClientboundEntityUpdates(MissionNpcs.GUIDE, MissionNpcs.ENGINEER);
            context.waitTicks(5);
            GuidedMissionChecks.capture(context, "astronaut-guide");
            world.getServer().runOnServer(server -> {
                final var player = world.getConnection().getServerPlayer();
                final var guides = player.level().getEntitiesOfClass(MissionAstronaut.class, new AABB(-8, 65, -8, 8, 70, 8));
                final var guide = guides.stream().filter(npc -> npc.getType() == MissionNpcs.GUIDE).findFirst().orElseThrow();
                guide.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            });
            context.waitForScreen(NpcDialogueScreen.class);
            context.clickScreenButton("Power advice");
            GuidedMissionChecks.capture(context, "astronaut-dialogue");
            context.setScreen(() -> null);
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -24 66 -3 0 20");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            GuidedMissionChecks.capture(context, "research-console-model");
            world.getServer().runCommand("mission equipment research true");
            // Wait out the solar particle storm inside the crew shelter, then return outside for the dust capture.
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 0.5 66 26.5 0 0");
            GuidedMissionChecks.advanceTo(world, MissionState.SOLAR_END_SECONDS);
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -24 66 -3 0 20");
            GuidedMissionChecks.advanceTo(world, 80);
            context.waitFor(client -> NimbusNovaClient.snapshot.state().elapsedSeconds() >= 80);
            context.waitTicks(100);
            GuidedMissionChecks.capture(context, "mars-dust-storm");

            world.getServer().runCommand("mission pause");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.PAUSED);
            final int pausedTime = context.computeOnClient(client -> NimbusNovaClient.snapshot.state().elapsedSeconds());
            context.waitTicks(25);
            if (context.computeOnClient(client -> NimbusNovaClient.snapshot.state().elapsedSeconds()) != pausedTime)
                throw new AssertionError("Mission time advanced during pause");

            world.getServer().runCommand("mission resume");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.ACTIVE);
            GuidedMissionChecks.repair(context, world, true, false);
            GuidedMissionChecks.advanceTo(world, MissionState.STORM_END_SECONDS);
            world.getServer().runCommand("mission equipment research true");
            GuidedMissionChecks.advanceTo(world, MissionState.DURATION_SECONDS);
            final var finalMission = world.getServer().computeOnServer(server -> MissionController.get(server).snapshot());
            if (finalMission.phase() != MissionState.Phase.SUCCESS)
                throw new AssertionError("The balanced guided attempt did not succeed: " + finalMission);
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.SUCCESS);
            context.waitForScreen(MissionConsoleScreen.class);
            GuidedMissionChecks.capture(context, "guided-mission-success");
            context.setScreen(() -> null);
            world.getServer().runCommand("mission reset");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.READY);
            world.getServer().runOnServer(server -> {
                final var player = world.getConnection().getServerPlayer();
                final var rovers = player.level().getEntitiesOfClass(MarsRover.class, new AABB(18, 64, 17, 38, 70, 34));
                final MarsRover rover = rovers.stream().findFirst().orElseThrow();
                final Vec3 before = rover.position();
                if (!player.startRiding(rover))
                    throw new AssertionError("The crew could not board the rover");

                player.setLastClientInput(new Input(true, false, false, false, false, false, false));
                for (int tick = 0; tick < 20; tick++)
                    rover.tick();

                if (rover.position().distanceToSqr(before) < 0.5)
                    throw new AssertionError("The rover did not move from its driver's forward input");

                player.stopRiding();
                player.setLastClientInput(Input.EMPTY);
                rover.tick();
                if (rover.getDeltaMovement().horizontalDistanceSqr() > 0.0001)
                    throw new AssertionError("An unoccupied rover continued driving");
            });
            RoverVisibilityChecks.captureDriverView(context, world);
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 24 68 19 -55 20");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            GuidedMissionChecks.capture(context, "aster-rover");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 211 "
                    + (MarsTerrain.surfaceHeight(211, -155) + 35) + " -155 90 5");
            world.getConnection().waitForChunksRender();
            GuidedMissionChecks.capture(context, "mars-terrain");
            world.getServer().runCommand("mission debug_start");
            world.getServer().runCommand("mission abort");
            context.waitForScreen(MissionConsoleScreen.class);
            GuidedMissionChecks.capture(context, "mission-debrief");
            context.clickScreenButton("Next");
            GuidedMissionChecks.capture(context, "mission-debrief-page-two");
            context.setScreen(() -> null);
            context.runOnClient(client -> client.gui.hud.toggle());
            world.getServer().runCommand("execute as @p run mars leave");
            context.waitFor(client -> client.level != null && !client.level.dimension().equals(MarsTrainingMap.DIMENSION));
            final MarsMovementChecks.Sample returnedMovement = world.getServer().computeOnServer(
                    server -> MarsMovementChecks.sample(world.getConnection().getServerLevel()));
            if (Math.abs(returnedMovement.gravity() - earthMovement.gravity()) > 0.000001
                    || Math.abs(returnedMovement.jumpHeight() - earthMovement.jumpHeight()) > 0.000001
                    || returnedMovement.fallDamage() != earthMovement.fallDamage())
                throw new AssertionError("Leaving Mars must restore ordinary movement and fall damage");
        }

        context.runOnClient(client -> {
            final JoinSessionScreen screen = new JoinSessionScreen(new TitleScreen());
            client.setScreenAndShow(screen);
            final EditBox field = (EditBox) screen.children().stream()
                    .filter(child -> child instanceof EditBox).findFirst().orElseThrow();
            field.setValue("Astronaut Ada");
            field.moveCursorToEnd(false);
            field.setHighlightPos(0);
            field.insertText("Crew 01");
            if (!field.getValue().equals("Crew 01"))
                throw new AssertionError("Join field lost native selection/replacement behavior");

            field.setFocused(true);
        });
        GuidedMissionChecks.capture(context, "join-session-msdf");
        context.clickScreenButton("Back");
        context.waitForScreen(TitleScreen.class);
        System.out.println("Guided mission smoke test passed: preparation, puzzles, shelter, relay repair, full success and report.");
    }
}
