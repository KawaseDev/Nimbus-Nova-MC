package cc.nimbusnova.mc.content;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class RepairContent {
    public static final Block PATCH_PLATE = registerPlate();
    public static final Item WIRE_KIT = registerWire();

    private RepairContent() {
        /* w */
    }

    private static Block registerPlate() {
        final Identifier id = Identifier.parse("nimbus_nova:repair_patch_plate");
        final ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        final Block block = Registry.register(BuiltInRegistries.BLOCK, key,
                new Block(BlockBehaviour.Properties.of().setId(key).strength(4).sound(SoundType.METAL)));
        Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, id),
                new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
        return block;
    }

    private static Item registerWire() {
        final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("nimbus_nova:repair_wire_kit"));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(PATCH_PLATE);
            output.accept(WIRE_KIT);
        });
    }
}
