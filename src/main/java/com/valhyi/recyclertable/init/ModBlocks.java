package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.RecyclerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RecyclerTable.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RecyclerTable.MOD_ID);

    public static final DeferredBlock<Block> RECYCLER_TABLE = BLOCKS.register("recycler_table",
            () -> new RecyclerBlock(BlockBehaviour.Properties.of().strength(2.5f)));

    // Vinculación segura de NeoForge que evita la lectura antes de tiempo
    public static final DeferredItem<BlockItem> RECYCLER_TABLE_ITEM = ITEMS.registerSimpleBlockItem("recycler_table", RECYCLER_TABLE);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
