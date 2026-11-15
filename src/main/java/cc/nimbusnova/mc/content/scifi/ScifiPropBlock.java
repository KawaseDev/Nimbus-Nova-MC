package cc.nimbusnova.mc.content.scifi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ScifiPropBlock extends Block {
    private final VoxelShape shape;
    private final @Nullable SimpleParticleType particle;
    private final int particleInterval;

    public ScifiPropBlock(final Properties properties, final double minX, final double minY, final double minZ,
                          final double maxX, final double maxY, final double maxZ) {
        this(properties, minX, minY, minZ, maxX, maxY, maxZ, null, 0);
    }

    /** A prop that also gives off an occasional ambient particle, one in {@code particleInterval} display ticks. */
    public ScifiPropBlock(final Properties properties, final double minX, final double minY, final double minZ,
                          final double maxX, final double maxY, final double maxZ,
                          final @Nullable SimpleParticleType particle, final int particleInterval) {
        super(properties.noOcclusion());
        shape = Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        this.particle = particle;
        this.particleInterval = particleInterval;
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos position,
                                  final CollisionContext context) {
        return shape;
    }

    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos position, final RandomSource random) {
        if (particle == null || random.nextInt(particleInterval) != 0) return;

        final var bounds = shape.bounds();
        level.addParticle(particle, position.getX() + bounds.minX + random.nextDouble() * bounds.getXsize(),
                position.getY() + (bounds.minY < 0.5 ? bounds.maxY + 0.05 : bounds.minY - 0.05),
                position.getZ() + bounds.minZ + random.nextDouble() * bounds.getZsize(),
                0, bounds.minY < 0.5 ? 0.02 : -0.02, 0);
    }
}
