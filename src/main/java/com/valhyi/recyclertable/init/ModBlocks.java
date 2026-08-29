package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.RecyclerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RecyclerTable.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RecyclerTable.MOD_ID);

    public static final DeferredBlock<RecyclerBlock> RECYCLER_TABLE = BLOCKS.registerBlock("recycler_table",
        RecyclerBlock::new,
        () -> BlockBehaviour.Properties.of()
            .strength(3.5f, 6.0f)
            .sound(SoundType.WOOD)
            .requiresCorrectToolForDrops()
    );

    public static final DeferredItem<BlockItem> RECYCLER_TABLE_ITEM = ITEMS.registerSimpleBlockItem("recycler_table", RECYCLER_TABLE);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
