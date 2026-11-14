package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.client.airlock.EntranceAirlockClient;
import cc.nimbusnova.mc.client.suit.SuitLifeSupportClient;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.suit.SuitLifeSupport;
import cc.nimbusnova.mc.world.EntranceAirlock;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;

final class AtmosphereChecks {
    private AtmosphereChecks() {
        /* w */
    }

    static void entrance(final ClientGameTestContext context, final TestSingleplayerContext world) {
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -0.5 66 -38.5 0 0");
        context.waitFor(client -> EntranceAirlockClient.scan() != null && EntranceAirlockClient.scan().stage() == 1);
        context.waitTicks(15);
        world.getServer().runOnServer(server -> {
            final var level = world.getConnection().getServerLevel();
            for (final int z : new int[] { -41, -35 }) {
                if (level.getBlockState(new BlockPos(0, 66, z)).getValue(DoorBlock.OPEN))
                    throw new AssertionError("The scan ran with a gate open");
            }
        });
        GuidedMissionChecks.capture(context, "entrance-dust-purge");
        context.waitFor(client -> EntranceAirlockClient.scan() != null && EntranceAirlockClient.scan().stage() == 2);
        world.getServer().runOnServer(server -> {
            final var level = world.getConnection().getServerLevel();
            if (!EntranceAirlock.pressurized(level, new BlockPos(0, 66, -38)))
                throw new AssertionError("The cleared chamber did not restore habitat air");
        });
        GuidedMissionChecks.capture(context, "entrance-scan-cleared");
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p -0.5 66 -30.5 0 0");
        context.waitTicks(25);
    }

    static void suit(final ClientGameTestContext context, final TestSingleplayerContext world) {
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 40 66 -16 90 0");
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            SuitLifeSupport.tickPlayer(player);
        });
        context.waitFor(client -> SuitLifeSupportClient.state() != null && !SuitLifeSupportClient.state().pressurized());
        final double sealed = SuitLifeSupportClient.state().air();
        if (!SuitLifeSupportClient.state().sealedSuit())
            throw new AssertionError("The preparation kit did not seal all four suit slots");
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            final float health = player.getHealth();
            for (int second = 0; second < 22; second++)
                SuitLifeSupport.tickPlayer(player);
            if (player.getHealth() != health)
                throw new AssertionError("Untimed preparation inflicted exposure damage");
        });
        context.waitFor(client -> SuitLifeSupportClient.state() != null && !SuitLifeSupportClient.state().helmet()
                && SuitLifeSupportClient.state().air() == 0);
        if (sealed <= SuitLifeSupportClient.state().air())
            throw new AssertionError("An unsealed helmet did not lose personal air");
        GuidedMissionChecks.capture(context, "outside-helmet-air-warning");
        world.getServer().runCommand("execute in nimbus_nova:mars_training run tp @p 0 66 0 0 0");
        world.getServer().runOnServer(server -> {
            final var player = world.getConnection().getServerPlayer();
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.SPACE_HELMET));
            for (int second = 0; second < 10; second++)
                SuitLifeSupport.tickPlayer(player);
        });
        context.waitFor(client -> SuitLifeSupportClient.state() != null && SuitLifeSupportClient.state().pressurized()
                && SuitLifeSupportClient.state().air() == 100);
        System.out.println("Atmosphere runtime checks passed: sealed scan gates, pressure recovery, outside air loss and safe preparation.");
    }
}
