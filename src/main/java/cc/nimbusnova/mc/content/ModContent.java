package cc.nimbusnova.mc.content;

import cc.nimbusnova.mc.mission.MissionController;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@NullMarked
public final class ModContent {
    private static final ArmorMaterial EXPEDITION_MATERIAL = new ArmorMaterial(18,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2, ArmorType.BODY, 5),
            9, SoundEvents.ARMOR_EQUIP_IRON, 0, 0,
            ItemTags.REPAIRS_IRON_ARMOR,
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse("nimbus_nova:expedition_suit")));

    public static final Item MISSION_TABLET = register("mission_tablet", properties -> new TabletItem(properties.stacksTo(1)
            .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.nimbus_nova.mission_tablet.description"))))));
    public static final Item OXYGEN_CANISTER = register("oxygen_canister", properties -> new OxygenCanisterItem(properties.stacksTo(16)
            .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.nimbus_nova.oxygen_canister.description"))))));
    public static final Item ENGINEERING_TOOL = register("engineering_tool", properties -> new Item(properties.pickaxe(ToolMaterial.IRON, 1, -2.8F)
            .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.nimbus_nova.engineering_tool.description"))))));
    public static final Item MARS_SAMPLE = register("mars_sample", properties -> new Item(properties.stacksTo(16)
            .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.nimbus_nova.mars_sample.description"))))));
    public static final Item SPACE_HELMET = register("space_helmet", properties -> suit(properties, ArmorType.HELMET));
    public static final Item SPACE_CHESTPLATE = register("space_chestplate", properties -> suit(properties, ArmorType.CHESTPLATE));
    public static final Item SPACE_LEGGINGS = register("space_leggings", properties -> suit(properties, ArmorType.LEGGINGS));
    public static final Item SPACE_BOOTS = register("space_boots", properties -> suit(properties, ArmorType.BOOTS));

    private ModContent() {
        /* w */
    }

    private static Item register(final String name, final Function<Item.Properties, Item> factory) {
        final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("nimbus_nova:" + name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
    }

    private static Item suit(final Item.Properties properties, final ArmorType type) {
        return new Item(properties.humanoidArmor(EXPEDITION_MATERIAL, type).component(DataComponents.LORE,
                new ItemLore(List.of(Component.translatable("item.nimbus_nova.space_suit.description")))));
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(MISSION_TABLET);
            output.accept(OXYGEN_CANISTER);
            output.accept(ENGINEERING_TOOL);
            output.accept(MARS_SAMPLE);
            output.accept(SPACE_HELMET);
            output.accept(SPACE_CHESTPLATE);
            output.accept(SPACE_LEGGINGS);
            output.accept(SPACE_BOOTS);
        });
    }

    private static final class TabletItem extends Item {
        private TabletItem(final Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer)
                MissionController.get(serverPlayer.level().getServer()).openConsole(serverPlayer);

            return InteractionResult.SUCCESS;
        }
    }

    private static final class OxygenCanisterItem extends Item {
        private OxygenCanisterItem(final Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

            if (!MissionController.get(serverPlayer.level().getServer()).replenishOxygen(serverPlayer))
                return InteractionResult.FAIL;

            final ItemStack stack = player.getItemInHand(hand);
            player.getCooldowns().addCooldown(stack, 20);
            stack.consume(1, player);
            return InteractionResult.SUCCESS_SERVER;
        }
    }
}
