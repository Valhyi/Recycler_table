package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecyclerBlockEntity>> RECYCLER_BLOCK_ENTITY =
    BLOCK_ENTITIES.register("recycler", () -> 
        BlockEntityType.Builder.of(RecyclerBlockEntity::new, ModBlocks.RECYCLER_BLOCK.get()).build(null)
    );
    public static final Supplier<BlockEntityType<RecyclerBlockEntity>> RECYCLER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("recycler_block_entity", () ->
                    BlockEntityType.Builder.of((pos, state) -> new RecyclerBlockEntity(pos, state), 
                            ModBlocks.RECYCLER_TABLE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
