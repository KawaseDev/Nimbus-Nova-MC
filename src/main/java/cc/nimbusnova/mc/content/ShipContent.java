package cc.nimbusnova.mc.content;

import cc.nimbusnova.mc.content.ship.ShipStationBlock;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public final class ShipContent {
    public static final Block PILOT_CONSOLE = register("pilot_console", Shapes.or(
            Block.box(1, 0, 2, 15, 2, 14),
            Block.box(5, 2, 8, 11, 7, 12),
            Block.box(1, 7, 3, 15, 9, 13),
            Block.box(2, 9, 9, 14, 15, 13),
            Block.box(3, 10, 8.7, 13, 14, 9),
            Block.box(3, 9, 4, 5, 9.7, 6),
            Block.box(6, 9, 4, 8, 9.7, 6),
            Block.box(10, 9, 4, 10.8, 12, 4.8),
            Block.box(9.5, 11.5, 3.6, 11.3, 12.8, 5.2),
            Block.box(1, 7, 3, 15, 7.6, 4)));
    public static final Block GALLEY = register("galley", Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(1, 2, 6, 7, 11, 15),
            Block.box(9, 2, 7, 15, 11, 15),
            Block.box(0, 11, 0, 16, 12, 16),
            Block.box(2, 12, 4, 7, 15, 9),
            Block.box(1.5, 15, 3.5, 7.5, 16, 9.5),
            Block.box(9, 7, 6.7, 15, 8, 7),
            Block.box(11, 12, 3, 14, 15, 6),
            Block.box(10.5, 15, 2.5, 14.5, 16, 6.5),
            Block.box(2, 5, 5.7, 6, 9, 6),
            Block.box(3, 6, 5.5, 5, 8, 5.7)));
    public static final Block MEDICAL_STATION = register("medical_station", Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(2, 2, 5, 14, 10, 14),
            Block.box(3, 3, 4.6, 13, 7, 5),
            Block.box(9, 4, 4.2, 12, 4.8, 4.6),
            Block.box(8, 10, 10, 10, 12, 12),
            Block.box(5, 12, 9, 14, 16, 13),
            Block.box(6, 12.5, 8.7, 13, 15.5, 9),
            Block.box(1, 10, 1, 6, 11, 12),
            Block.box(2, 11, 2, 5, 11.8, 9),
            Block.box(2, 11, 13, 4, 15, 15)));
    public static final Block MEETING_TERMINAL = register("meeting_terminal", Shapes.or(
            Block.box(4, 0, 4, 12, 2, 12),
            Block.box(2, 0, 6, 14, 2, 10),
            Block.box(6, 2, 6, 10, 8, 10),
            Block.box(2, 8, 2, 14, 10, 14),
            Block.box(3, 8, 1, 13, 9, 2),
            Block.box(5, 10, 5, 11, 12, 11),
            Block.box(3, 10, 3, 4, 10.6, 4),
            Block.box(12, 10, 3, 13, 10.6, 4),
            Block.box(3, 10, 12, 4, 10.6, 13),
            Block.box(12, 10, 12, 13, 10.6, 13)));
    public static final Block ENGINE_CORE = register("engine_core", Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(3, 3, 3, 13, 14, 13),
            Block.box(2, 4, 5, 14, 13, 11),
            Block.box(5, 4, 2, 11, 13, 14),
            Block.box(6, 2, 6, 10, 15, 10),
            Block.box(0, 2, 2, 2, 14, 14),
            Block.box(14, 2, 2, 16, 14, 14),
            Block.box(0, 14, 2, 16, 16, 14),
            Block.box(4, 5, 0, 12, 12, 2),
            Block.box(5, 6, 0.8, 11, 11, 1),
            Block.box(3, 2, 2, 13, 3, 14),
            Block.box(3, 13, 2, 13, 14, 14),
            Block.box(2, 6, 4.7, 3, 11, 5),
            Block.box(13, 6, 4.7, 14, 11, 5)));
    public static final Block SCIENCE_LAB = register("science_lab", Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(5, 2, 9, 11, 9, 14),
            Block.box(0, 9, 1, 16, 11, 15),
            Block.box(2, 11, 3, 8, 12, 9),
            Block.box(3.5, 12, 4.5, 5.5, 15, 6.5),
            Block.box(3, 15, 4, 6, 16, 7),
            Block.box(10, 11, 10, 12, 15, 12),
            Block.box(7, 14, 8, 12, 16, 12),
            Block.box(7, 12, 7, 9, 15, 9),
            Block.box(11, 11, 2, 15, 12, 6),
            Block.box(12, 12, 3, 14, 12.5, 5)));
    public static final Block COMMS_ARRAY = register("comms_array", Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(7, 2, 8, 9, 10, 10),
            Block.box(3, 8, 6, 13, 9, 12),
            Block.box(2, 9, 9, 14, 13, 11),
            Block.box(4, 13, 10, 12, 15, 12),
            Block.box(3, 9.5, 8.7, 6, 12.5, 9),
            Block.box(7, 9.5, 8.7, 10, 12.5, 9),
            Block.box(11, 9.5, 8.7, 13, 12.5, 9),
            Block.box(1, 4, 6, 3, 8, 12),
            Block.box(13, 4, 6, 15, 8, 12),
            Block.box(3, 3, 1, 13, 5, 5),
            Block.box(4, 5, 2, 12, 5.6, 4),
            Block.box(7, 15, 10, 9, 16, 12)));
    public static final Block HYDROPONICS = register("hydroponics", Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(1, 3, 1, 15, 4, 15),
            Block.box(6, 4, 1, 10, 4.5, 15),
            Block.box(3, 4, 3, 4, 11, 4),
            Block.box(2, 7, 2, 5, 8, 5),
            Block.box(2.5, 10, 2.5, 4.5, 11, 4.5),
            Block.box(3, 4, 12, 4, 11, 13),
            Block.box(2, 7, 11, 5, 8, 14),
            Block.box(2.5, 10, 11.5, 4.5, 11, 13.5),
            Block.box(12, 4, 3, 13, 11, 4),
            Block.box(11, 7, 2, 14, 8, 5),
            Block.box(11.5, 10, 2.5, 13.5, 11, 4.5),
            Block.box(12, 4, 12, 13, 11, 13),
            Block.box(11, 7, 11, 14, 8, 14),
            Block.box(11.5, 10, 11.5, 13.5, 11, 13.5),
            Block.box(14, 4, 7, 16, 10, 9),
            Block.box(14.3, 5, 6.8, 15.7, 9, 7),
            Block.box(5, 1, 0, 11, 2, 0.2)));
    public static final Block PUNCHING_BAG = register("punching_bag", Shapes.or(
            Block.box(4, 15, 4, 12, 16, 12),
            Block.box(7, 12, 7, 9, 15, 9),
            Block.box(4, 2, 5, 12, 12, 11),
            Block.box(5, 2, 4, 11, 12, 12),
            Block.box(5, 1, 5, 11, 2, 11),
            Block.box(3.8, 4, 4.8, 12.2, 5, 11.2),
            Block.box(4.8, 4, 3.8, 11.2, 5, 12.2),
            Block.box(3.8, 9, 4.8, 12.2, 10, 11.2),
            Block.box(4.8, 9, 3.8, 11.2, 10, 12.2),
            Block.box(6, 6, 3.8, 10, 8, 4)));
    public static final Block STORAGE_LOCKER = register("storage_locker", Shapes.or(
            Block.box(1, 0, 2, 15, 2, 14),
            Block.box(1, 2, 3, 3, 16, 14),
            Block.box(13, 2, 3, 15, 16, 14),
            Block.box(3, 2, 12, 13, 16, 14),
            Block.box(3, 2, 3, 13, 3, 12),
            Block.box(3, 14, 3, 13, 16, 12),
            Block.box(3, 3, 2, 13, 14, 3),
            Block.box(11, 6, 1.2, 12, 10, 2),
            Block.box(4, 10, 1.8, 6, 11, 2),
            Block.box(4, 5, 1.8, 9, 5.4, 2),
            Block.box(4, 7, 1.8, 9, 7.4, 2)));

    private ShipContent() {
        /* w */
    }

    private static Block register(final String name, final VoxelShape shape) {
        final Identifier identifier = Identifier.parse("nimbus_nova:ship_" + name);
        final ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, identifier);
        final Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new ShipStationBlock(
                BlockBehaviour.Properties.of().setId(blockKey).mapColor(MapColor.COLOR_LIGHT_BLUE)
                        .strength(3.0F).sound(SoundType.METAL)
                        .lightLevel(state -> state.getValue(BlockStateProperties.POWERED) ? 9 : 1), shape));
        final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, identifier);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output ->
                List.of(PILOT_CONSOLE, GALLEY, MEDICAL_STATION, MEETING_TERMINAL, ENGINE_CORE,
                        SCIENCE_LAB, COMMS_ARRAY, HYDROPONICS, PUNCHING_BAG, STORAGE_LOCKER).forEach(output::accept));
    }
}
