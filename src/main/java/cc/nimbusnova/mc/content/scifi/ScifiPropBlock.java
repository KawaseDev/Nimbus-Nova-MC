package cc.nimbusnova.mc.content.scifi;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ScifiPropBlock extends Block {
    private final VoxelShape shape;

    public ScifiPropBlock(final Properties properties, final double minX, final double minY, final double minZ,
                          final double maxX, final double maxY, final double maxZ) {
        super(properties.noOcclusion());
        shape = Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos position,
                                  final CollisionContext context) {
        return shape;
    }
}
