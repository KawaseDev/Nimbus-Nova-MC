package cc.nimbusnova.mc.scenario;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ScenarioContent {
    private static final Identifier ID = Identifier.parse("nimbus_nova:scenario_shield");
    private static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, ID);
    public static final Block SHIELD = Registry.register(BuiltInRegistries.BLOCK, KEY,
            new Block(BlockBehaviour.Properties.of().setId(KEY).strength(3)));
    static {
        final ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, ID);
        Registry.register(BuiltInRegistries.ITEM, item, new BlockItem(SHIELD, new Item.Properties().setId(item).useBlockDescriptionPrefix()));
    }
    private ScenarioContent() {
        /* w */
    }
    public static void register() {
        /* w */
    }
}
