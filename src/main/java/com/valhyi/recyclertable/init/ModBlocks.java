package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.RecyclerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RecyclerTable.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, RecyclerTable.MOD_ID);

    public static final DeferredBlock<Block> RECYCLER_TABLE = BLOCKS.register("recycler_table",
            () -> new RecyclerBlock(BlockBehaviour.Properties.of().strength(2.5f)));

    public static final DeferredHolder<Item, Item> RECYCLER_TABLE_ITEM = ITEMS.register("recycler_table",
            () -> new BlockItem(RECYCLER_TABLE.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
