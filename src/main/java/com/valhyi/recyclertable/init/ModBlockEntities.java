package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RecyclerTable.MOD_ID);

    public static final Supplier<BlockEntityType<RecyclerBlockEntity>> RECYCLER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("recycler_block_entity", () ->
                    new BlockEntityType<>(RecyclerBlockEntity::new, ModBlocks.RECYCLER_TABLE.get()));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
