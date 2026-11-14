package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class MissionClientSmokeTest implements FabricClientGameTest {
    @Override
    public void runTest(final ClientGameTestContext context) {
        try (final TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> NimbusNovaClient.snapshot != null);
            world.getServer().runOnServer(server -> server.getPlayerList().op(new NameAndId(world.getConnection().getServerPlayer().getGameProfile())));
            world.getServer().runCommand("mars create");
            world.getServer().runCommand("mars create");
            world.getServer().runCommand("execute as @p run mars join");
            context.waitFor(client -> client.level != null && client.level.dimension().equals(MarsTrainingMap.DIMENSION));
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

            world.getServer().runCommand("mission pause");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.PAUSED);
            final int pausedTime = context.computeOnClient(client -> NimbusNovaClient.snapshot.state().elapsedSeconds());
            context.waitTicks(25);
            if (context.computeOnClient(client -> NimbusNovaClient.snapshot.state().elapsedSeconds()) != pausedTime)
                throw new AssertionError("Mission time advanced during pause");

            world.getServer().runCommand("mission reset");
            context.waitFor(client -> NimbusNovaClient.snapshot.state().phase() == MissionState.Phase.READY);
            world.getServer().runCommand("execute as @p run mars leave");
            context.waitFor(client -> client.level != null && !client.level.dimension().equals(MarsTrainingMap.DIMENSION));
        }
    }
}
