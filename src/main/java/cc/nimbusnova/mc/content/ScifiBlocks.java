package cc.nimbusnova.mc.content;

import cc.nimbusnova.mc.content.scifi.AirlockDoorBlock;
import cc.nimbusnova.mc.content.scifi.ScifiPropBlock;
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
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.function.Function;

@NullMarked
public final class ScifiBlocks {
    public static final Block DOME_GLASS = register("dome_glass", properties -> new HalfTransparentBlock(properties
            .sound(SoundType.GLASS).strength(1.5F).noOcclusion()), 0);
    public static final Block HULL_PANEL = register("hull_panel", Block::new, 0);
    public static final Block REINFORCED_HULL = register("reinforced_hull", Block::new, 0);
    public static final Block FLOOR_TILE = register("floor_tile", Block::new, 0);
    public static final Block FLOOR_LIGHT = register("floor_light", Block::new, 12);
    public static final Block STRIP_LIGHT = register("strip_light", properties -> new ScifiPropBlock(properties, 0, 0, 0, 16, 3, 16), 13);
    public static final Block SUPPORT_BEAM = register("support_beam", properties -> new ScifiPropBlock(properties, 3, 0, 3, 13, 16, 13), 0);
    public static final Block CABLE_TRUNK = register("cable_trunk", properties -> new ScifiPropBlock(properties, 0, 0, 0, 16, 7, 16), 0);
    public static final Block VENT = register("vent", properties -> new ScifiPropBlock(properties, 0, 0, 0, 16, 5, 16), 0);
    public static final Block BATTERY_BANK = register("battery_bank", properties -> new ScifiPropBlock(properties, 0, 0, 0, 16, 16, 16), 3);
    public static final Block AIR_TANK = register("air_tank", properties -> new ScifiPropBlock(properties, 2, 0, 2, 14, 16, 14), 0);
    public static final Block CRYO_CABINET = register("cryo_cabinet", properties -> new ScifiPropBlock(properties, 0, 0, 0, 16, 16, 16), 4);
    public static final Block HYDROPONIC_TRAY = register("hydroponic_tray", properties -> new ScifiPropBlock(properties, 0, 0, 0, 16, 6, 16), 0);
    public static final Block AIRLOCK_DOOR = register("airlock_door", AirlockDoorBlock::new, 0);

    private ScifiBlocks() {
        /* w */
    }

    private static Block register(final String name, final Function<BlockBehaviour.Properties, Block> factory, final int light) {
        final Identifier identifier = Identifier.parse("nimbus_nova:scifi_" + name);
        final ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, identifier);
        final Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of()
                .setId(key).mapColor(MapColor.COLOR_LIGHT_GRAY).sound(SoundType.METAL).strength(3.0F).lightLevel(_ -> light)));
        final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, identifier);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block,
                new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output ->
                List.of(DOME_GLASS, HULL_PANEL, REINFORCED_HULL, FLOOR_TILE, FLOOR_LIGHT, STRIP_LIGHT,
                        SUPPORT_BEAM, CABLE_TRUNK, VENT, BATTERY_BANK, AIR_TANK, CRYO_CABINET,
                        HYDROPONIC_TRAY, AIRLOCK_DOOR).forEach(output::accept));
    }
}
