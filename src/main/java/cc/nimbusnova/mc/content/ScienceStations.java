package cc.nimbusnova.mc.content;

import cc.nimbusnova.mc.lesson.LessonActionPayload;
import cc.nimbusnova.mc.lesson.LessonModel;
import cc.nimbusnova.mc.lesson.LessonPayload;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.HashMap;
import java.util.Map;

public final class ScienceStations {
    public static final Block AIRLOCK_CONTROL = registerBlock("lesson_airlock_control");
    public static final Block BACKUP_POWER = registerBlock("lesson_backup_power");
    public static final Block SHIELD_BENCH = registerBlock("lesson_shield_bench");
    private static final Map<MinecraftServer, Map<BlockPos, LessonModel>> SESSIONS = new HashMap<>();

    private ScienceStations() {
        /* w */
    }

    private static Block registerBlock(final String name) {
        final Identifier identifier = Identifier.parse("nimbus_nova:" + name);
        final ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, identifier);
        final Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of()
                .setId(key).strength(-1.0F, 3600000).sound(SoundType.METAL).noOcclusion().lightLevel(_ -> 5)));
        final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, identifier);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(LessonPayload.TYPE, LessonPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LessonActionPayload.TYPE, LessonActionPayload.CODEC);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(AIRLOCK_CONTROL);
            output.accept(BACKUP_POWER);
            output.accept(SHIELD_BENCH);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || kind(level.getBlockState(hit.getBlockPos()).getBlock()) < 0) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer && valid(serverPlayer, hit.getBlockPos()))
                send(serverPlayer, hit.getBlockPos(), "Practice freely before launch. Shared resources matter during the mission.");
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(LessonActionPayload.TYPE, (packet, context) -> context.server().execute(() -> {
            final ServerPlayer player = context.player();
            if (!valid(player, packet.position())) return;
            final LessonModel model = model(player, packet.position());
            final MissionController controller = MissionController.get(context.server());
            final MissionState.Phase phase = controller.snapshot().phase();
            if (phase != MissionState.Phase.READY && phase != MissionState.Phase.ACTIVE) {
                send(player, packet.position(), "Experiments are available during preparation or an active mission.");
                return;
            }
            if (packet.revision() != model.revision) {
                send(player, packet.position(), "Another crewmate changed this station. Check its new state.");
                return;
            }
            String feedback = "Choose a control to investigate.";
            switch (kind(player.level().getBlockState(packet.position()).getBlock())) {
                case 0 -> {
                    if (packet.action() == 0) {
                        model.isolated = true;
                        feedback = "Model hatches sealed. Now change chamber pressure before cycling.";
                    } else if (packet.action() == 1 || packet.action() == 2) {
                        feedback = model.adjustPressure(packet.action() == 1 ? -1 : 1)
                                ? "Match the target pressure before cycling. These are model units."
                                : "Seal the model hatches first, or pump in the other direction.";
                    } else if (packet.action() == 3) {
                        if (!model.canCycle())
                            feedback = "Unsafe model pressure difference! Match the target pressure before cycling.";
                        else if (controller.cycleAirlock(player)) {
                            model.isolated = false;
                            model.target = 100 - model.target;
                            feedback = phase == MissionState.Phase.READY ? "Practice cycle complete. Real airlocks isolate and equalize before opening."
                                    : "Cycle complete: 2 shared oxygen used. Air recovery reduces losses, never eliminates them.";
                        } else
                            feedback = "Not enough shared oxygen for a safe cycle.";
                    }
                }
                case 1 -> {
                    if (packet.action() == 0)
                        feedback = phase == MissionState.Phase.READY
                                ? "Practice transfer: active missions have only two +10 backup cells. No reserve consumed."
                                : controller.useBackupPower(player)
                                ? "One finite reserve cell transferred: +10 battery. Save the other for an emergency."
                                : "Transfer rejected: battery full or no reserve cells left.";
                }
                case 2 -> {
                    if (packet.action() >= 0 && packet.action() < 3) {
                        model.changeLayer(packet.action());
                        feedback = "Lower detector index, greater carried mass. Compare combinations; this is an authored model.";
                    }
                }
                default -> { /* w */ }
            }
            model.revision++;
            send(player, packet.position(), feedback);
        }));
    }

    private static int kind(final Block block) {
        return block == AIRLOCK_CONTROL ? 0 : block == BACKUP_POWER ? 1 : block == SHIELD_BENCH ? 2 : -1;
    }

    private static boolean valid(final ServerPlayer player, final BlockPos position) {
        return player.isAlive() && !player.isSpectator() && player.level().dimension().equals(MarsTrainingMap.DIMENSION)
                && player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) <= 36
                && kind(player.level().getBlockState(position).getBlock()) >= 0;
    }

    private static LessonModel model(final ServerPlayer player, final BlockPos position) {
        return SESSIONS.computeIfAbsent(player.level().getServer(), _ -> new HashMap<>())
                .computeIfAbsent(position.immutable(), _ -> new LessonModel());
    }

    private static void send(final ServerPlayer player, final BlockPos position, final String feedback) {
        final LessonModel model = model(player, position);
        final MissionController controller = MissionController.get(player.level().getServer());
        ServerPlayNetworking.send(player, new LessonPayload(position, kind(player.level().getBlockState(position).getBlock()),
                model.revision, model.pressure, model.target, model.isolated, model.layers, controller.backupCellsRemaining(),
                (int) controller.snapshot().battery(), feedback));
    }
}
