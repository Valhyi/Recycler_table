package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.RecyclerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

import java.util.function.Supplier;

public class ModBlocks {
    // ES: Registros diferidos para Bloques e Items
    // EN: Deferred registers for Blocks and Items
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RecyclerTable.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RecyclerTable.MOD_ID);

    // ES: Registro del bloque Mesa Recicladora
    // EN: Recycler Table block registration
    public static final DeferredBlock<Block> RECYCLER_TABLE = registerBlock("recycler_table", RecyclerBlock::new);

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier) {
        DeferredBlock<T> registeredBlock = BLOCKS.register(name, blockSupplier);
        ITEMS.register(name, () -> new BlockItem(registeredBlock.get(), new Item.Properties()));
        return registeredBlock;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
