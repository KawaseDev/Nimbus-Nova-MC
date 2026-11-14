package cc.nimbusnova.mc.content;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.Station;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
public final class ModBlocks {
    public static final Block RESEARCH_CONSOLE = register("research_console", Station.RESEARCH);
    public static final Block OXYGEN_CONSOLE = register("oxygen_console", Station.OXYGEN);

    private ModBlocks() {
        /* w */
    }

    private static Block register(final String name, final Station equipment) {
        final Identifier identifier = Identifier.parse("nimbus_nova:" + name);
        final ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, identifier);
        final Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new EquipmentBlock(equipment,
                BlockBehaviour.Properties.of().setId(blockKey).mapColor(MapColor.COLOR_LIGHT_GRAY)
                        .strength(2.5F).sound(SoundType.METAL).noOcclusion()
                        .lightLevel(state -> state.getValue(BlockStateProperties.POWERED) ? 7 : 0)));

        Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, identifier),
                new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, identifier)).useBlockDescriptionPrefix()));
        return block;
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(RESEARCH_CONSOLE);
            output.accept(OXYGEN_CONSOLE);
        });
        UseBlockCallback.EVENT.register((_, level, hand, hit) -> {
            if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel serverLevel
                    && serverLevel.getBlockState(hit.getBlockPos()).getBlock() instanceof EquipmentBlock equipment)
                MissionController.get(serverLevel.getServer()).bindStation(GlobalPos.of(level.dimension(), hit.getBlockPos()), equipment.equipment);

            return InteractionResult.PASS;
        });
    }

    public static void updateState(final ServerLevel level, final BlockPos position, final boolean online) {
        final BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof EquipmentBlock) || state.getValue(BlockStateProperties.POWERED) == online) return;

        level.setBlock(position, state.setValue(BlockStateProperties.POWERED, online), Block.UPDATE_CLIENTS);
    }

    private static final class EquipmentBlock extends HorizontalDirectionalBlock {
        private final Station equipment;
        private final Map<Direction, VoxelShape> shapes;

        private EquipmentBlock(final Station equipment, final Properties properties) {
            super(properties);
            this.equipment = equipment;
            shapes = Shapes.rotateHorizontal(equipment == Station.RESEARCH
                    ? Shapes.or(Block.box(1, 0, 2, 15, 3, 14), Block.box(5, 3, 6, 11, 9, 11), Block.box(1, 8, 3, 15, 15, 14))
                    : Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(1, 2, 4, 6, 16, 12), Block.box(6, 2, 3, 15, 15, 13)));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, BlockStateProperties.POWERED);
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
        protected void onPlace(final BlockState state, final Level level, final BlockPos position, final BlockState oldState, final boolean moved) {
            if (oldState.is(this) || !(level instanceof ServerLevel serverLevel)) return;

            MissionController.get(serverLevel.getServer()).bindStation(GlobalPos.of(level.dimension(), position), equipment);
        }

        @Override
        protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel level, final BlockPos position, final boolean moved) {
            super.affectNeighborsAfterRemoval(state, level, position, moved);
            final Block replacement = level.getBlockState(position).getBlock();
            if (replacement == this) return;

            final MissionController controller = MissionController.get(level.getServer());
            if (replacement instanceof EquipmentBlock machine)
                controller.bindStation(GlobalPos.of(level.dimension(), position), machine.equipment);
            else
                controller.unbindStation(GlobalPos.of(level.dimension(), position));
        }

        @Override
        protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder parameters) {
            return List.of(new ItemStack(this));
        }
    }
}
