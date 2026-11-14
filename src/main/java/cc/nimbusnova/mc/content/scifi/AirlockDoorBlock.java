package cc.nimbusnova.mc.content.scifi;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AirlockDoorBlock extends DoorBlock {
    public AirlockDoorBlock(final Properties properties) {
        super(BlockSetType.COPPER, properties.noOcclusion());
    }
}
