package cc.nimbusnova.mc.scenario;

import cc.nimbusnova.mc.content.ship.ShipStationBlock;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
    private static final Identifier LAMP_ID = Identifier.parse("nimbus_nova:scenario_grow_light");
    private static final ResourceKey<Block> LAMP_KEY = ResourceKey.create(Registries.BLOCK, LAMP_ID);
    public static final Block GROW_LIGHT = Registry.register(BuiltInRegistries.BLOCK, LAMP_KEY,
            new ShipStationBlock(BlockBehaviour.Properties.of().setId(LAMP_KEY).strength(2)
                    .lightLevel(state -> state.getValue(BlockStateProperties.POWERED) ? 13 : 0), Block.box(0, 13, 0, 16, 16, 16)));
    static {
        final ResourceKey<Item> lampItem = ResourceKey.create(Registries.ITEM, LAMP_ID);
        Registry.register(BuiltInRegistries.ITEM, lampItem, new BlockItem(GROW_LIGHT, new Item.Properties().setId(lampItem).useBlockDescriptionPrefix()));
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
