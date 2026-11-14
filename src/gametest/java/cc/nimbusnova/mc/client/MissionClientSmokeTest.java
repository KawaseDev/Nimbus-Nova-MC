package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.mission.CrewRole;
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
import net.minecraft.client.gui.screens.TitleScreen;
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
        try (final TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> NimbusNovaClient.snapshot != null);
            final MarsMovementChecks.Sample earthMovement = world.getServer().computeOnServer(
                    server -> MarsMovementChecks.sample(world.getConnection().getServerLevel()));
            world.getServer().runOnServer(server -> server.getPlayerList().op(new NameAndId(world.getConnection().getServerPlayer().getGameProfile())));
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
            context.takeScreenshot("mars-outpost");
            if (MissionTutorial.consoleOpened)
                throw new AssertionError("The tutorial must start with the console step unchecked");

            context.takeScreenshot("tutorial-start");
            context.setScreen(MissionConsoleScreen::new);
            context.clickScreenButton("Systems");
            context.clickScreenButton("Power engineer");
            context.waitFor(client -> NimbusNovaClient.snapshot.role() == CrewRole.POWER);
            context.setScreen(() -> null);
            context.takeScreenshot("tutorial-ready");
            if (!MissionTutorial.consoleOpened)
                throw new AssertionError("Opening the console must tick the tutorial's first step");

            context.setScreen(MissionConsoleScreen::new);
            context.clickScreenButton("Start mission");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.ACTIVE);
            context.setScreen(MissionConsoleScreen::new);
            context.takeScreenshot("mission-overview");
            context.clickScreenButton("Systems");
            context.clickScreenButton("Research: ON");
            context.waitFor(client -> !NimbusNovaClient.snapshot.state().researchOnline());
            world.getServer().runOnServer(server -> {
                if (world.getConnection().getServerLevel().getBlockState(MarsTrainingMap.RESEARCH_STATION).getValue(BlockStateProperties.POWERED))
                    throw new AssertionError("Research console animation must follow the server equipment state");
            });
            context.takeScreenshot("mission-systems");
            context.clickScreenButton("Briefing");
            context.takeScreenshot("mission-briefing");
            context.setScreen(() -> null);
            context.takeScreenshot("mission-hud");
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
            context.takeScreenshot("expedition-equipment");
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
            if (!context.computeOnClient(client -> NimbusNovaClient.snapshot.state().researchOnline()))
                throw new AssertionError("The power engineer could not operate its bound research station");

            if (!context.computeOnClient(client -> NimbusNovaClient.snapshot.state().oxygenOnline()))
                throw new AssertionError("The power engineer was allowed to operate the oxygen station");

            if (context.computeOnClient(client -> NimbusNovaClient.snapshot.state().phase()) != MissionState.Phase.ACTIVE)
                throw new AssertionError("A non-operator reset the mission");

            context.runOnClient(client -> client.gui.hud.toggle());
            world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -3.5 66 -6.5 0 0");
            world.getConnection().waitForClientboundEntityUpdates(MissionNpcs.GUIDE, MissionNpcs.ENGINEER);
            context.waitTicks(5);
            context.takeScreenshot("astronaut-guide");
            world.getServer().runOnServer(server -> {
                final var player = world.getConnection().getServerPlayer();
                final var guides = player.level().getEntitiesOfClass(MissionAstronaut.class, new AABB(-8, 65, -8, 8, 70, 8));
                final var guide = guides.stream().filter(npc -> npc.getType() == MissionNpcs.GUIDE).findFirst().orElseThrow();
                guide.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            });
            context.waitForScreen(NpcDialogueScreen.class);
            context.clickScreenButton("Power advice");
            context.takeScreenshot("astronaut-dialogue");
            context.setScreen(() -> null);
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -24 66 -3 0 20");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("research-console-model");
            world.getServer().runCommand("mission equipment research false");
            world.getServer().runOnServer(server -> {
                for (int tick = 0; tick < 1600; tick++)
                    ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
            });
            context.waitFor(client -> NimbusNovaClient.snapshot.state().elapsedSeconds() >= 80);
            context.waitTicks(100);
            context.takeScreenshot("mars-dust-storm");

            world.getServer().runCommand("mission pause");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.PAUSED);
            final int pausedTime = context.computeOnClient(client -> NimbusNovaClient.snapshot.state().elapsedSeconds());
            context.waitTicks(25);
            if (context.computeOnClient(client -> NimbusNovaClient.snapshot.state().elapsedSeconds()) != pausedTime)
                throw new AssertionError("Mission time advanced during pause");

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
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 24 68 19 -55 20");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("aster-rover");
            world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 211 "
                    + (MarsTerrain.surfaceHeight(211, -155) + 35) + " -155 90 5");
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("mars-terrain");
            world.getServer().runCommand("mission start");
            world.getServer().runCommand("mission abort");
            context.waitForScreen(MissionConsoleScreen.class);
            context.takeScreenshot("mission-debrief");
            context.clickScreenButton("Next");
            context.takeScreenshot("mission-debrief-page-two");
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
            final MissionEditBox field = (MissionEditBox) screen.children().stream()
                    .filter(child -> child instanceof MissionEditBox).findFirst().orElseThrow();
            field.setValue("Astronaut Ada");
            field.moveCursorToEnd(false);
            field.setHighlightPos(0);
            field.insertText("Crew 01");
            if (!field.getValue().equals("Crew 01"))
                throw new AssertionError("Custom MSDF field lost native selection/replacement behavior");

            field.setFocused(true);
        });
        context.takeScreenshot("join-session-msdf");
        context.clickScreenButton("Back");
        context.waitForScreen(TitleScreen.class);
    }
}
