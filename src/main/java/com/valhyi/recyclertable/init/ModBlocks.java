package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.RecyclerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RecyclerTable.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RecyclerTable.MOD_ID);

    // Se usa tu clase RecyclerBlock y el método auxiliar para registrar también su BlockItem automáticamente
    public static final DeferredBlock<Block> RECYCLER_TABLE = registerBlock("recycler_table",
        () -> new RecyclerBlock(BlockBehaviour.Properties.of().strength(3.0f))
    );

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
