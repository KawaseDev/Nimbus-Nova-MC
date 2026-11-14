package cc.nimbusnova.mc.content;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionPreparation;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

@NullMarked
public final class OutpostContent {
    public static final Block SAMPLE_DEPOSIT = register("sample_deposit", MissionPreparation.TaskKind.COLLECT_SAMPLE);
    public static final Block SAMPLE_ANALYZER = register("sample_analyzer", MissionPreparation.TaskKind.DELIVER_SAMPLE);
    public static final Block SUPPLY_LOCKER = register("outpost_supply_locker", MissionPreparation.TaskKind.SUPPLIES);
    public static final Block WEATHER_TERMINAL = register("outpost_weather_terminal", MissionPreparation.TaskKind.WEATHER_SCAN);
    public static final Block HOLOGRAM_PROJECTOR = registerProjector();

    private OutpostContent() {
        /* w */
    }

    private static Block register(final String name, final MissionPreparation.TaskKind task) {
        final Identifier identifier = Identifier.parse("nimbus_nova:" + name);
        final ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, identifier);
        final Block block = Registry.register(BuiltInRegistries.BLOCK, key, new OutpostBlock(task,
                BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_LIGHT_GRAY)
                        .strength(2.5F).sound(task == MissionPreparation.TaskKind.COLLECT_SAMPLE ? SoundType.STONE : SoundType.METAL)
                        .noOcclusion().lightLevel(state -> task != MissionPreparation.TaskKind.COLLECT_SAMPLE
                                && state.getValue(BlockStateProperties.OPEN) ? 6 : 0)));
        Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, identifier),
                new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, identifier)).useBlockDescriptionPrefix()));
        return block;
    }

    private static Block registerProjector() {
        final Identifier identifier = Identifier.parse("nimbus_nova:outpost_hologram_projector");
        final ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, identifier);
        final Block block = Registry.register(BuiltInRegistries.BLOCK, key, new ProjectorBlock(BlockBehaviour.Properties.of()
                .setId(key).mapColor(MapColor.COLOR_CYAN).strength(2.5F).sound(SoundType.METAL).noOcclusion().lightLevel(_ -> 6)));
        Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, identifier),
                new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, identifier)).useBlockDescriptionPrefix()));
        return block;
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(SAMPLE_DEPOSIT);
            output.accept(SAMPLE_ANALYZER);
            output.accept(SUPPLY_LOCKER);
            output.accept(WEATHER_TERMINAL);
            output.accept(HOLOGRAM_PROJECTOR);
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, position, state, _) -> {
            if (!level.dimension().equals(MarsTrainingMap.DIMENSION) || Math.abs(position.getX()) > 48 || Math.abs(position.getZ()) > 48
                    || !(player instanceof ServerPlayer serverPlayer)
                    || serverPlayer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return true;

            if (!(state.getBlock() instanceof OutpostBlock) && !(state.getBlock() instanceof DoorBlock)
                    && !state.is(ModBlocks.RESEARCH_CONSOLE) && !state.is(ModBlocks.OXYGEN_CONSOLE)) return true;

            serverPlayer.sendOverlayMessage(Component.literal("Right-click equipment to use it. Mission equipment is protected."));
            return false;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            final BlockState state = level.getBlockState(hit.getBlockPos());
            if (state.is(HOLOGRAM_PROJECTOR)) {
                if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer)
                    MissionController.get(serverPlayer.level().getServer()).openConsole(serverPlayer);

                return InteractionResult.SUCCESS;
            }

            if (!(state.getBlock() instanceof OutpostBlock block)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel))
                return InteractionResult.SUCCESS;

            if (MissionController.get(serverLevel.getServer()).interaction(serverPlayer, block.task)
                    && block.task != MissionPreparation.TaskKind.COLLECT_SAMPLE) {
                serverLevel.setBlock(hit.getBlockPos(), state.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_CLIENTS);
                serverLevel.scheduleTick(hit.getBlockPos(), block, 40);
            }

            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static final class ProjectorBlock extends Block {
        private ProjectorBlock(final Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos position, final CollisionContext context) {
            return Block.box(2, 0, 2, 14, 4, 14);
        }

        @Override
        protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder parameters) {
            return List.of(new ItemStack(this));
        }
    }

    private static final class OutpostBlock extends HorizontalDirectionalBlock {
        private final MissionPreparation.TaskKind task;
        private final Map<Direction, VoxelShape> shapes;

        private OutpostBlock(final MissionPreparation.TaskKind task, final Properties properties) {
            super(properties);
            this.task = task;
            shapes = Shapes.rotateHorizontal(switch (task) {
                case COLLECT_SAMPLE -> Shapes.or(Block.box(1, 0, 1, 15, 5, 15), Block.box(4, 5, 4, 12, 10, 12));
                case DELIVER_SAMPLE -> Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(3, 3, 5, 13, 12, 14), Block.box(2, 12, 6, 14, 16, 14));
                case WEATHER_SCAN -> Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(5, 3, 5, 11, 12, 11), Block.box(2, 12, 4, 14, 16, 13));
                default -> Block.box(1, 0, 2, 15, 16, 14);
            });
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, BlockStateProperties.OPEN);
        }

        @Override
        public BlockState getStateForPlacement(final BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos position, final CollisionContext context) {
            return shapes.get(state.getValue(FACING));
        }

        @Override
        protected void tick(final BlockState state, final ServerLevel level, final BlockPos position, final RandomSource random) {
            if (state.getValue(BlockStateProperties.OPEN))
                level.setBlock(position, state.setValue(BlockStateProperties.OPEN, false), Block.UPDATE_CLIENTS);
        }

        @Override
        protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder parameters) {
            return List.of(new ItemStack(this));
        }
    }
}
