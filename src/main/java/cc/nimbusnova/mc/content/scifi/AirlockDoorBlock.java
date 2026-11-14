package cc.nimbusnova.mc.content.scifi;

import cc.nimbusnova.mc.world.EntranceAirlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AirlockDoorBlock extends DoorBlock {
    public AirlockDoorBlock(final Properties properties) {
        super(BlockSetType.COPPER, properties.noOcclusion().lightLevel(state -> state.getValue(OPEN) ? 3 : 11));
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos position,
                           final BlockState previous, final boolean moved) {
        if (level instanceof ServerLevel server && state.getValue(HALF) == DoubleBlockHalf.LOWER)
            server.scheduleTick(position, this, 5);
    }

    @Override
    protected void tick(final BlockState state, final ServerLevel level, final BlockPos position, final RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return;

        final Boolean chamber = EntranceAirlock.openingOverride(level, position);
        final boolean nearby = chamber != null ? chamber : !level.getEntitiesOfClass(Player.class,
                new AABB(position).inflate(2.5, 1, 2.5), player -> player.isAlive() && !player.isSpectator()).isEmpty();
        if (nearby != state.getValue(OPEN)) {
            level.setBlock(position, state.setValue(OPEN, nearby), Block.UPDATE_ALL);
            final BlockState upper = level.getBlockState(position.above());
            if (upper.is(this))
                level.setBlock(position.above(), upper.setValue(OPEN, nearby), Block.UPDATE_ALL);
        }

        level.scheduleTick(position, this, 5);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos position, final CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? Block.box(0, 0, 7, 16, 16, 9) : Block.box(7, 0, 0, 9, 16, 16);
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos position, final CollisionContext context) {
        final Direction facing = state.getValue(FACING);
        final boolean left = (state.getValue(HINGE) == DoorHingeSide.LEFT)
                != (facing == Direction.SOUTH || facing == Direction.WEST);
        final VoxelShape post;
        if (state.getValue(FACING).getAxis() == Direction.Axis.Z)
            post = Block.box(left ? 0 : 13, 0, 2, left ? 3 : 16, 16, 14);
        else
            post = Block.box(2, 0, left ? 0 : 13, 14, 16, left ? 3 : 16);

        final int bottom = state.getValue(HALF) == DoubleBlockHalf.LOWER ? 0 : 15;
        final VoxelShape rim = facing.getAxis() == Direction.Axis.Z
                ? Block.box(0, bottom, 2, 16, bottom + 1, 14) : Block.box(2, bottom, 0, 14, bottom + 1, 16);
        return state.getValue(OPEN) ? Shapes.or(post, rim) : Shapes.or(post, rim, getShape(state, level, position, context));
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos position,
                                               final Player player, final BlockHitResult hit) {
        return InteractionResult.SUCCESS;
    }
}
