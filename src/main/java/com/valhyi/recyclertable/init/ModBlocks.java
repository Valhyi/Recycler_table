package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.RecyclerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RecyclerTable.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RecyclerTable.MOD_ID);

    // ES: requiresCorrectToolForDrops() = el bloque SOLO suelta el item si se
    // rompe con la herramienta correcta. Las herramientas correctas y el nivel
    // minimo (hierro o superior) se definen con tags de datapack:
    //   data/minecraft/tags/block/mineable/axe.json
    //   data/minecraft/tags/block/mineable/pickaxe.json
    //   data/minecraft/tags/block/needs_iron_tool.json
    // Con otra herramienta (o a mano) el bloque se destruye igual, pero el item
    // no cae; el contenido del inventario SIEMPRE cae (ver
    // RecyclerBlockEntity.preRemoveSideEffects) y la loot table
    // data/recyclertable/loot_table/blocks/recycler_table.json define el drop.
    public static final DeferredBlock<RecyclerBlock> RECYCLER_TABLE = BLOCKS.register("recycler_table",
            key -> new RecyclerBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, key))
                    .strength(2.5f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> RECYCLER_TABLE_ITEM = ITEMS.register("recycler_table",
            key -> new BlockItem(RECYCLER_TABLE.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
